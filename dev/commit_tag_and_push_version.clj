(ns commit-tag-and-push-version
  (:require [orzo.core :as orzo]
            [orzo.git :as git]))

(defn -main [& args]
  (try
    (println (-> (orzo/unstage)
                 (orzo/prepend "v")
                 (git/commit)
                 (git/tag)
                 (git/push-tag)))
    (System/exit 0)
    (catch Exception e
      (println e)
      (System/exit 1))))
