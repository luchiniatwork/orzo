(ns gen-version
  (:require [orzo.core :as orzo]
            [orzo.git :as git]))

(defn -main [& args]
  (try
    (println (-> (orzo/read-file "BASE_VERSION.txt")
                 (orzo/set-semver {:patch (orzo/env "CIRCLE_BUILD_NUM")})
                 (orzo/stage)))
    (System/exit 0)
    (catch Exception e
      (println e)
      (System/exit 1))))
