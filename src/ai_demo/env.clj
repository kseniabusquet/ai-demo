(ns ai-demo.env)

(defn openai-api-key [] (or (System/getenv "OPENAI_API_KEY") ""))
