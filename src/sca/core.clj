(ns sca.core
  (:require [reaver :refer [parse extract extract-from text attr]]
            [hiccup.core :refer [html]]
            [clj-http.client :as client])
  (:gen-class))

(def ^:private root "https://www.destroyallsoftware.com")
(def ^:private directory "output_destroy/")
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
                ".duration" text))

(defn- top-catalog []
  (extract-from parsed-catalog ".catalog_by_season > .season"
                [:header :episodes]
                "h1 > img" (attr :alt)
                ".episodes" extract-episode))

(defn- episode-page
  "Download each catalog page downloads the video into videos/ directory.
  Returns a map with the catalog page info and video file path"
  [catalog-episode]
  (let [parsed (-> catalog-episode :sub-url url slurp parse)
        js-parser (fn [node]
                    (->> node
                         .toString
                         (re-find #"[^\"]*resolution\=4k")))
        video-url (str "videos/"
                       (second (re-find #"/catalog/(.*)" (:sub-url catalog-episode)))
                       ".mp4")]
    (Thread/sleep 20000) ; sleep 20s to avoid DoS the server
    (->
     (extract parsed
              [:description-html :video-id :video-source]
              ".details" #(when %1 (.toString %1))
              "video" (attr :id)
              ; video source presents a little fun challenge for us
              ; since the source is actually dynamically injected by javascript, we parse the js!
              ".catalog_show_page > .row > script" js-parser)
     (merge {:video-url video-url})
     ((fn [details]
        (file-download
         (url (:video-source details))
         (str directory (:video-url details)))
        details))
     (merge catalog-episode))))
; (episode-page {:sub-url "/screencasts/catalog/pretty-git-logs" :id "test-id"})

#_(generate-hiccup (clojure.edn/read-string (slurp (str directory "destroy-all-episodes.edn"))))
(defn- generate-hiccup [catalog]
  (let [css ".season { padding-top: 50px; } .button { background: #606c76; border: none; box-shadow: 0 0 2px grey;}"
        episode-mapper #(identity [:section
                                   [:div.row
                                    [:div.column.column-33 {:style "font-weight: bold;"} (:title %1)]
                                    [:div.column.column-57 (:sub-title %1)]
                                    [:div.column.column-10 {:style "text-align: right;"}
                                     [:a.button.afterglow {:target "_blank" :href (:video-url %1)} "View"]]]
                                    [:div.row
                                     [:div.column (:description-html %1)]]])
        season-mapper #(identity [:div.container.season
                                  [:h3(:header %1)]
                                  (map episode-mapper (:episodes %1))])]
    (->>
     (html
      [:html
       [:link {:rel "stylesheet" :href "//fonts.googleapis.com/css?family=Roboto:300,300italic,700,700italic"}]
       [:link {:rel "stylesheet" :href "//cdn.rawgit.com/necolas/normalize.css/master/normalize.css"}]
       [:link {:rel "stylesheet" :href "//cdn.rawgit.com/milligram/milligram/master/dist/milligram.min.css"}]
       [:style css]
       [:body {:style "padding: 50px 0 100px 0;"}
        [:h2 {:style "text-align: center;"} [:a {:href root} root]]
        [:div
         (map season-mapper catalog)]
        ]])
     (spit (str directory "index.html")))))

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
    (generate-hiccup full)))
