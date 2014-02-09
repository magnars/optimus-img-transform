(ns optimus-img-transform.core
  (:require [fivetonine.collage.util :as util]
            [fivetonine.collage.core :as collage]
            [optimus.paths :refer [filename-ext just-the-path just-the-filename]]
            [optimus.assets.creation :refer [last-modified]]
            [clojure.java.io :as io]
            [clojure.set :as set]))

(defn- path-with-metadata [path image quality options]
  (let [extension (filename-ext path)
        base (subs path 0 (- (count path) (count extension) 1))]
    (str base
         "-" quality
         (if-let [scale (:scale options)] (str "-x" scale) "")
         (if-let [width (:width options)] (str "-w" width) "")
         (if-let [height (:height options)] (str "-h" height) "")
         (case (:progressive options)
           true "-p" ;; progressive
           false "-b" ;; baseline
           nil "")
         "-" (last-modified image)
         "." extension)))

(defn- create-folders [path]
  (.mkdirs (.getParentFile (io/file path))))

(defn- transform-image-1 [image tmp-path quality options]
  (create-folders tmp-path)
  (-> (util/load-image image)
      (cond->
       (:scale options) (collage/scale (:scale options))
       (or (:width options) (:height options)) (collage/resize :width (:width options) :height (:height options)))
      (util/save tmp-path
                 :quality quality
                 :progressive (:progressive options))))

(defn transform-image [path image tmp-dir quality & [options]]
  (when (and (:scale options) (or (:width options) (:height options)))
    (throw (Exception. "Setting both :scale and :width / :height does not compute.")))
  (let [tmp-path (str tmp-dir (path-with-metadata path image quality options))
        tmp-file (io/file tmp-path)]
    (if (.exists tmp-file)
      tmp-path
      (transform-image-1 image tmp-path quality options))))

(defn- add-filename-prefix [path prefix]
  (str (just-the-path path) prefix (just-the-filename path)))

(defn- transform-asset [asset options]
  (if-not (re-find (:regexp options) (:path asset))
    asset
    (if-not (:resource asset)
      (throw (Exception. (str "Your transform-images regexp matches a non-binary asset: " (:path asset))))
      (let [path (transform-image (:path asset) (:resource asset) (:tmp-dir options) (:quality options) options)]
        (-> asset
            (assoc :resource (io/as-url (str "file:" path)))
            (assoc ::transformed true)
            (cond-> (:prefix options) (update-in [:path] #(add-filename-prefix % (:prefix options)))))))))

(defn- enforce-required-options [options required fn-name]
  (let [missing (set/difference required (set (keys options)))]
    (when-not (empty? missing)
      (throw (Exception. (str "Required options " missing " for " fn-name " are missing."))))))

(def default-options
  {:tmp-dir (System/getProperty "java.io.tmpdir")})

(def required-options
  #{:regexp :quality})

(defn transform-images [assets options]
  (enforce-required-options options required-options "transform-images")
  (let [options (merge default-options options)
        transformed (->> assets
                         (remove ::transformed)
                         (map #(transform-asset % options)))]
    (if (:prefix options)
      (concat assets transformed)
      (concat (filter ::transformed assets) transformed))))
