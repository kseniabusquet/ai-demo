# Video-to-Transcript and Summaries (Clojure Project)

A Clojure-based pipeline that:
1. Finds all `.mp4` videos in a specified folder.
2. (Optionally) extracts/compresses audio to `.mp3` if needed.
3. (Optionally) Transcribes the audio using [OpenAI Whisper](https://platform.openai.com/docs/api-reference/audio) if needed.
4. (Optionally) Summarizes the transcript using GPT (e.g., GPT-3.5, GPT-4, or custom models) if needed.
5. Produces a consolidated Markdown file listing all summaries in order.
6. (Optionally) tracks token usage and approximate cost for GPT-based summaries.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Usage](#usage)
- [Configuration](#configuration)
- [Parallelization](#parallelization)
- [Token Usage \& Cost Tracking](#token-usage--cost-tracking)

---

## Prerequisites

1. **Clojure** (tested on Clojure 1.11.1).
2. **Leiningen** or the **Clojure CLI** tools (for running the project).
3. **ffmpeg** and **ffprobe** for extracting audio from videos and (optionally) generating screenshots.
   - On Ubuntu/Debian:
     
     ```bash
     sudo apt-get install ffmpeg
     ```
4. **OpenAI Account** with an **API Key** and sufficient quota.
   - The Whisper endpoint has a 25 MB file-size limit.
   - GPT endpoints have token/usage constraints and associated costs.

---

## Installation

1. **Clone** this repository
2. Ensure you have Leiningen or the Clojure CLI installed. For example:
   # On Ubuntu/Debian
   
   ```bash
    sudo apt-get install leiningen
   ```
4. Confirm ffmpeg is installed:
   
   ```bash
   ffmpeg -version
   ```

---

## Usage

1. Set your OpenAI API Key in the environment:
   
  ```bash
  export OPENAI_API_KEY="sk-..."
  ```
2. Run the script using Leiningen:
   
   ```bash
   lein run /path/to/folder-with-mp4s
   ```
4. The script will:
- Detect .mp4 files in the given folder.
- Create subfolders transcripts and summaries (if not present).
- For each video:
  - If an .mp3 isn’t found, extract and compress audio to .mp3 (lower bitrate).
  - Transcribe via the Whisper endpoint (if no existing transcript file is found).
  - Summarize the transcript with GPT (if no existing summary file is found).
  - Write a final Markdown file named after the folder (e.g., folder-name.md).
  - Print the total time taken and the total approximate cost (WIP)
  - Example Output:
    
    ```
    folder-name.md in the same directory, containing structured Markdown with a heading for each video plus screenshots (if enabled).
    ```
  - Subfolders:
    
    ```
    transcripts/: Contains *_transcript.txt files.
    summaries/: Contains *_summary.md files.
    ```
---

## Configuration
1. Audio Bitrate: In the extract-audio function (e.g., -b:a 64k), you can increase or decrease bitrate to manage file size vs. audio quality.
2. Parallel Processing: By default, the code uses pmap or a custom thread pool. If you hit rate limits, reduce concurrency or add backoff.

---

## Parallelization

- The script processes videos in parallel via pmap.
- If you have many large videos, consider a custom thread pool or concurrency-limiting approach to avoid 429 Too Many Requests.
- The final Markdown is still assembled in sorted order (lexicographically by filename).

---

## Token Usage & Cost Tracking
- For GPT endpoints, the script can parse the "usage" field (prompt_tokens, completion_tokens, total_tokens) from each API response.
- These tokens can be accumulated in an atom. At the end, you can estimate cost based on your model’s rates.
- Whisper usage is billed by audio minute, so usage info typically won’t appear in the response—check your OpenAI usage dashboard.




