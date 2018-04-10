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
  (let [css ".on { display: block; } .off { display: none; }"
        button-js "document.querySelector('div.on') && (document.querySelector('div.on').className='off');
                   this.parentElement.parentElement.nextSibling.className='on';
                   document.querySelector('.button-on') && (document.querySelector('.button-on').className='button button-outline');
                   this.className='button button-on';"
        episode-mapper #(identity [:section
                                   [:div.row
                                    [:div.column.column-33 {:style "font-weight: bold;"} (:title %1)]
                                    [:div.column.column-57 (:sub-title %1)]
                                    [:div.column.column-10 {:style "text-align: right;"}
                                     [:a.button.button-outline {:onclick button-js} "View"]]]
                                   [:div.off
                                    [:div.row
                                     [:div.column (:description-html %1)]]
                                    [:div.row
                                     [:div.column
                                      [:video {:src (:video-url %1) :width "1080" :controls "yes"}]]]]])
        season-mapper #(identity [:div.container {:style "margin-bottom: 3em;"}
                                  [:h1(:header %1)]
                                  (map episode-mapper (:episodes %1))])]
    (->>
     (html
      [:html
       [:link {:rel "stylesheet" :href "//fonts.googleapis.com/css?family=Roboto:300,300italic,700,700italic"}]
       [:link {:rel "stylesheet" :href "//cdn.rawgit.com/necolas/normalize.css/master/normalize.css"}]
       [:link {:rel "stylesheet" :href "//cdn.rawgit.com/milligram/milligram/master/dist/milligram.min.css"}]
       [:style css]
       [:body {:style "margin-top: 6em"}
        (map season-mapper catalog)]])
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
