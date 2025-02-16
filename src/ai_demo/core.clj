(ns ai-demo.core
  (:require
   [ai-demo.env :as env]
   [clj-http.client :as http]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def openai-api-url "https://api.openai.com/v1")
(def video-folder "/mnt/e/")

(defn humanize-ms
  "Convert a duration in milliseconds to a human-readable string.
   e.g. '01:23:45' or '2 days, 03:01:10' if >24h."
  [millis]
  (let [total-secs (quot millis 1000)
        s         (mod total-secs 60)
        total-min (quot total-secs 60)
        m         (mod total-min 60)
        total-hrs (quot total-min 60)
        h         (mod total-hrs 24)
        d         (quot total-hrs 24)]
    (if (pos? d)
      (format "%d days, %02d:%02d:%02d" d h m s)
      (format "%02d:%02d:%02d" h m s))))

(defn extract-audio
  "Given the path to a .mp4 file (video-file),
   extract and compress the audio to .mp3 (64kbps)
   in the same directory, returning the path to the resulting .mp3 file."
  [video-file]
  (let [f         (io/file video-file)
        parent    (.getParent f)
        base-name (str/replace (.getName f) #"\.mp4$" "")
        out-path  (str parent "/audios/" base-name ".mp3")
        cmd       ["ffmpeg"
                   "-i" (.getAbsolutePath f)
                   "-vn"
                   "-acodec" "libmp3lame"
                   "-b:a" "64k"
                   out-path]
        process (.exec (Runtime/getRuntime) (into-array String cmd))]
    (.waitFor process)
    out-path))

(defn- whisper-transcribe
  "Given a path to an MP3 (or other audio) file, call OpenAI Whisper API
   to get a transcript. Returns the transcription text on success."
  [file-path api-key]
  (let [url  (str openai-api-url "/audio/transcriptions")
        opts {:headers   {"Authorization" (str "Bearer " api-key)}
              :multipart [{:name      "file"
                           :content   (io/file file-path)
                           :mime-type "audio/mpeg"}
                          {:name    "model"
                           :content "whisper-1"}]
              :as        :json}
        response (http/post url opts)
        body     (:body response)]
    (if-let [error (:error body)]
      (throw (ex-info "Failed to transcribe audio" {:error error}))
      (:text body))))

(defn- summarize-transcript
  "Send the transcript to the ChatCompletion API (GPT-3.5 or GPT-4) to summarize
   and generate detailed bullet points, headings, etc. The language can often be
   auto-detected by the model. We'll instruct it to produce markdown."
  [transcript api-key]
  (let [system-prompt  (str "You are a helpful assistant that creates detailed study notes in markdown. "
                            "You receive a transcript of a lecture or talk. Please summarize the main points, "
                            "add bullet points, headings, italic text for key terms, and provide a thorough, "
                            "structured study guide in Markdown. Feel free to include helpful subheadings as needed.")
        user-message   (str "Here is the transcript in Brazilian Portuguese:\n\n"
                            transcript
                            "\n\nPlease produce a detailed markdown summary in Braziian Portuguese with headings, bullet points, etc. "
                            "Include relevant structure for studying (like H2, bullet points, numbered lists, bold, italics).No need to add ```markdown to your response.")
        {:keys [body]} (http/post (str openai-api-url "/chat/completions")
                                  {:headers {"Authorization" (str "Bearer " api-key)
                                             "Content-Type"  "application/json"}
                                   :body    (json/encode
                                             {:model       "gpt-4o-mini"
                                              :messages    [{:role "system"
                                                             :content system-prompt}
                                                            {:role "user"
                                                             :content user-message}]
                                              :temperature 0.7
                                              :max_tokens  1500})
                                   :as      :json})]
    (if-let [error (:error body)]
      (throw (ex-info "Failed to summarize transcript" {:error error}))
      (-> body
          :choices
          first
          :message
          :content))))

(defn process-one-video
  "Process a single .mp4 video:
   1) Extract .mp3 audio if not already present
   2) Transcribe with Whisper if not already present
   3) Summarize with ChatGPT if not already present
   4) Save raw transcript & summary to disk if not already present
   5) Return a Markdown snippet."
  [video-file api-key]
  (let [video-path      (.getAbsolutePath video-file)
        parent-dir      (.getParent (io/file video-file))
        name-video      (.getName video-file)
        base-name       (str/replace name-video #"\.mp4$" "")
        mp3-path        (str parent-dir "/audios/" base-name ".mp3")
        audio-file      (io/file mp3-path)
        audio-path      (if (.exists audio-file)
                          audio-file
                          (extract-audio video-path))
        transcript-path (str parent-dir "/transcripts/" base-name "_transcript.txt")
        summary-path    (str parent-dir "/summaries/" base-name "_summary.md")
        transcript-file (io/file transcript-path)
        summary-file    (io/file summary-path)
        ->summary-md    #(str "## " name-video "\n\n" % "\n\n")]
    (cond
      (or (and (.exists transcript-file) (.exists summary-file))
          (.exists summary-file))
      (->summary-md (slurp summary-file))

      (and (.exists transcript-file) (not (.exists summary-file)))
      (let [summary (summarize-transcript (slurp transcript-file) api-key)]
        (spit summary-path summary)
        (->summary-md summary))

      (not (and (.exists transcript-file) (.exists summary-file)))
      (let [transcript (whisper-transcribe audio-path api-key)
            summary    (summarize-transcript transcript api-key)]
        (spit transcript-path transcript)
        (spit summary-path summary)
        (->summary-md summary)))))

(defn process-videos-parallel
  "Processes all .mp4 files in a folder in parallel, combining their results into
   a single markdown file. Uses pmap for parallelism."
  [folder-path]
  (let [start-time  (System/currentTimeMillis)
        folder      (clojure.java.io/file folder-path)
        _           (.mkdirs (clojure.java.io/file folder-path "transcripts"))
        _           (.mkdirs (clojure.java.io/file folder-path "summaries"))
        _           (.mkdirs (clojure.java.io/file folder-path "audios"))
        api-key     (env/openai-api-key)
        video-files (->> (.listFiles folder)
                         (filter #(and (.isFile %)
                                       (clojure.string/ends-with? (.getName %) ".mp4")))
                         (sort-by #(.getName %)))
        _ (prn "------------------")
        _ (prn (format "Found %s video files: %s" (count video-files) (mapv #(.getName %) video-files)))
        _ (prn "------------------")
        all-markdown-snippets (doall (pmap #(process-one-video % api-key) video-files))
        final-markdown        (str "# Resumos dos vídeos\n\n"
                                   (clojure.string/join "\n\n---\n\n" all-markdown-snippets))
        folder-name           (.getName (clojure.java.io/file folder-path))
        output-file           (str folder-path "/" folder-name ".md")
        end-time              (System/currentTimeMillis)
        elapsed               (- end-time start-time)]
    (spit output-file final-markdown)
    (prn "------------------")
    (prn "Output written to:" output-file)
    (prn "------------------")
    (prn (format "Total processing time for folder '%s': %s" folder-path (humanize-ms elapsed)))
    (prn "------------------")))

(defn -main
  "Entry point for CLI usage. Example:
  lein run /path/to/folder-of-videos
  "
  [& args]
  (let [[folder-path] args]
    (when (nil? folder-path)
      (prn "------------------")
      (prn "Usage: -main folder-path")
      (prn "------------------")
      (System/exit 1))
    (process-videos-parallel (str video-folder folder-path))
    (System/exit 0)))
