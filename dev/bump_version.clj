(ns bump-version
  (:require [orzo.core :as orzo]
            [orzo.git :as git]))

(defn -main [& args]
  (try
    (println (-> (orzo/read-file "VERSION.txt")
                 (orzo/calver "YY.MM.CC")
                 (orzo/overwrite-file "README.md" #"\"\d+.\d+.\d+\""
                                      (fn [x _] (str "\"" x "\"")))
                 (orzo/overwrite-file "pom.xml"
                                      #"<artifactId>orzo</artifactId>\n(\s*)<version>.+</version>"
                                      (fn [x m]
                                        (str "<artifactId>orzo</artifactId>\n$1"
                                             "<version>" x "</version>")
                                        #_(println "aqui" (second m))
                                        #_(str "<artifactId>orzo</artifactId>\n"
                                               (second m) "<version>" x "</version>")))
                 (orzo/save-file "VERSION.txt")
                 (orzo/stage)))
    (System/exit 0)
    (catch Exception e
      (println e)
      (System/exit 1))))
