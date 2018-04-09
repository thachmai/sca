(ns sca.core
  (:require [reaver :refer [parse extract extract-from text attr]]
            [hiccup.core :refer [html]]
            [clj-http.client :as client])
  (:gen-class))

(def ^:private root "https://www.destroyallsoftware.com")
(def ^:private directory "output/")
(defn- url [& _] (apply str root _))
(def ^:private catalog (slurp (url "/screencasts/catalog")))
(def ^:private parsed-catalog (parse catalog))

(defn- file-download [url path]
  (-> (client/get url {:as :stream :headers {"User-Agent" "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:59.0) Gecko/20100101 Firefox/59.0"}})
      (:body)
      (clojure.java.io/copy (clojure.java.io/file path)))
  nil)
; (file-download "https://ia800209.us.archive.org/24/items/WildlifeSampleVideo/Wildlife.ogv" "videos/wild.ogv")

(defn- extract-episode [source]
  (extract-from source ".episode"
                [:sub-url :title :sub-title :duration]
                "a" (attr :href)
                ".title" text
                ".subtitle" text
                ".duration" text
                ))

(defn- top-catalog []
  (extract-from parsed-catalog ".catalog_by_season > .season"
                [:header :episodes]
                "h1 > img" (attr :alt)
                ".episodes" extract-episode
                ))

(defn- episode-page
  "Download each catalog page downloads the video into videos/ directory.
  Returns a map with the catalog page info and video file path"
  [catalog-episode]
  (let [parsed (-> catalog-episode :sub-url url slurp parse)
        js-parser (fn [node]
                    (->> node
                         .toString
                         (re-find #"[^\"]*resolution\=4k")))]
    (println "Processing " catalog-episode)
    (Thread/sleep 20000) ; sleep 20s to avoid DoS the server
    (->
     (extract parsed
              [:description-html :video-id :video-source]
              ".details" #(when %1 (.toString %1))
              "video" (attr :id)
              ; video source presents a little fun challenge for us
              ; since the source is actually dynamically injected by javascript, we parse the js!
              ".catalog_show_page > .row > script" js-parser
              )
     ((fn [details]
        (file-download
         (url (:video-source details))
         (str directory "videos/" (:video-id details) ".mp4"))
        details))
     (merge catalog-episode))))
; (catalog-page {:sub-url "/screencasts/catalog/pretty-git-logs" :id "test-id"})

#_(def full (slurp (str directory "destroy-all-episodes.edn")))
#_(defn- generate-hiccup [catalog]
  (let [season-mapper #([:h1 ])])
  (html
   [:html
    (map #() catalog)
    ]))

(defn -main
  "Execution entry point"
  [& args]
  (clojure.java.io/make-parents (str directory "videos/dummy"))
  (let [top (top-catalog)
        top-to-full (fn [catalog]
                      (let [episodes (map episode-page (:episodes catalog))]
                        (merge catalog {:episodes episodes})))
        full (map top-to-full top)]
    (clojure.pprint/pprint full (clojure.java.io/writer (str directory "destroy-all-episodes.edn")))
    ))
