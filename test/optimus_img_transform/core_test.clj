(ns optimus-img-transform.core-test
  (:require [optimus-img-transform.core :refer :all :as core]
            [midje.sweet :refer :all]
            [test-with-files.core :refer [with-tmp-dir tmp-dir]]
            [fivetonine.collage.util :as util]
            [optimus.assets.creation :refer [last-modified]]
            [clojure.java.io :as io]))

(def timestamp (last-modified (io/resource "optimus.jpg")))

(fact
 "When transforming an image, it is saved in its new form in the given
  directory, with some metadata attached to the filename. This is
  later used to check for an existing version."

 (with-tmp-dir
   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2) => (str tmp-dir "/optimus-0.2-" timestamp ".jpg")

   (io/as-file (str tmp-dir "/optimus-0.2-" timestamp ".jpg")) => #(.exists %)

   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.3
                    {:scale 2
                     :progressive false}) => (str tmp-dir "/optimus-0.3-x2-b-" timestamp ".jpg")

                     (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.4
                                      {:width 200
                                       :height 100
                                       :progressive true}) => (str tmp-dir "/optimus-0.4-w200-h100-p-" timestamp ".jpg")))

(fact
 "It makes no sense giving both :scale and either :width or :height."

 (with-tmp-dir
   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.3
                    {:scale 2
                     :width 100}) => (throws Exception "Setting both :scale and :width / :height does not compute.")))

(fact
 "If the file already exists, it is not recomputed."

 (with-tmp-dir
   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2)

   (with-redefs [core/transform-image-1 (fn [_] (throw (Exception. "transform-image-1 should not be called")))]
     (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2) => (str tmp-dir "/optimus-0.2-" timestamp ".jpg"))))

(fact
 "That is, unless it's been changed in the meantime. It looks at the
  last-modified timestamp to determine that."

 (with-tmp-dir
   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2)

   (with-redefs [last-modified (fn [_] 1337)
                 core/transform-image-1 (fn [_ _ _ _] "It's transformed anew.")]

     (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2) => "It's transformed anew.")))

(fact
 "Nested paths work too."

 (with-tmp-dir
   (transform-image "/images/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2) => (str tmp-dir "/images/optimus-0.2-" timestamp ".jpg")

   (io/as-file (str tmp-dir "/images/optimus-0.2-" timestamp ".jpg")) => #(.exists %)))

(fact
 "Reduced quality is good for file size."

 (with-tmp-dir
   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2)
   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.3)

   (<
    (.length (io/as-file (str tmp-dir "/optimus-0.2-" timestamp ".jpg")))
    (.length (io/as-file (str tmp-dir "/optimus-0.3-" timestamp ".jpg")))) => true))

(fact
 "Scaling and resizing works."

 (with-tmp-dir
   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2)
   (let [img (util/load-image (str tmp-dir "/optimus-0.2-" timestamp ".jpg"))]
     [(.getWidth img) (.getHeight img)] => [343 400])

   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2 {:scale 2})
   (let [img (util/load-image (str tmp-dir "/optimus-0.2-x2-" timestamp ".jpg"))]
     [(.getWidth img) (.getHeight img)] => [686 800])

   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2 {:width 100})
   (let [img (util/load-image (str tmp-dir "/optimus-0.2-w100-" timestamp ".jpg"))]
     [(.getWidth img) (.getHeight img)] => [100 116])

   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2 {:width 100 :height 100})
   (let [img (util/load-image (str tmp-dir "/optimus-0.2-w100-h100-" timestamp ".jpg"))]
     [(.getWidth img) (.getHeight img)] => [100 100])

   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2 {:height 100})
   (let [img (util/load-image (str tmp-dir "/optimus-0.2-h100-" timestamp ".jpg"))]
     [(.getWidth img) (.getHeight img)] => [85 100])))

(fact
 "Cropping works"

 (with-tmp-dir
   (transform-image "/optimus.jpg" (io/resource "optimus.jpg") tmp-dir 0.2 {:crop {:size [100 50]
                                                                                   :offset [30 20]}})
   (let [img (util/load-image (str tmp-dir "/optimus-0.2-c30x20x100x50-" timestamp ".jpg"))]
     [(.getWidth img) (.getHeight img)] => [100 50])))

(fact
 "It transforms assets by regexp."

 (with-tmp-dir
   (transform-images [{:path "/images/optimus.jpg" :resource (io/resource "optimus.jpg")}
                      {:path "/photos/optimus.jpg" :resource (io/resource "optimus.jpg")}]
                     {:tmp-dir tmp-dir
                      :regexp #"/photos/.+\.jpg$"
                      :quality 0.2})
   => [{:path "/images/optimus.jpg" :resource (io/resource "optimus.jpg")}
       {:path "/photos/optimus.jpg" :resource (io/as-url (str "file:" tmp-dir "/photos/optimus-0.2-" timestamp ".jpg")) :optimus-img-transform.core/transformed true}]))

(fact
 "You can specify a prefix for the transformed file paths."

 (with-tmp-dir
   (transform-images [{:path "/optimus.jpg" :resource (io/resource "optimus.jpg")}]
                     {:tmp-dir tmp-dir
                      :regexp #"/.+\.jpg$"
                      :quality 0.2
                      :width 290
                      :prefix "w290-"})
   => [{:path "/optimus.jpg" :resource (io/resource "optimus.jpg")}
       {:path "/w290-optimus.jpg" :resource (io/as-url (str "file:" tmp-dir "/optimus-0.2-w290-" timestamp ".jpg")) :optimus-img-transform.core/transformed true}]))

(fact
 "You can do multiple operations on the same images with different
  prefixes. It won't touch images that it has already transformed."

 (with-tmp-dir
   (-> [{:path "/optimus.jpg" :resource (io/resource "optimus.jpg")}]
       (transform-images {:tmp-dir tmp-dir :regexp #"/.+\.jpg$" :quality 0.2 :width 290 :prefix "w290-"})
       (transform-images {:tmp-dir tmp-dir :regexp #"/.+\.jpg$" :quality 0.2 :width 580 :prefix "w580-"})
       (transform-images {:tmp-dir tmp-dir :regexp #"/.+\.jpg$" :quality 0.2}))
   => [{:path "/w290-optimus.jpg" :resource (io/as-url (str "file:" tmp-dir "/optimus-0.2-w290-" timestamp ".jpg")) :optimus-img-transform.core/transformed true}
       {:path "/w580-optimus.jpg" :resource (io/as-url (str "file:" tmp-dir "/optimus-0.2-w580-" timestamp ".jpg")) :optimus-img-transform.core/transformed true}
       {:path "/optimus.jpg" :resource (io/as-url (str "file:" tmp-dir "/optimus-0.2-" timestamp ".jpg")) :optimus-img-transform.core/transformed true}]))

(fact
 "There are some options that are required."

 (transform-images [] {}) => (throws Exception "Required options #{:quality :regexp} for transform-images are missing.")
 (transform-images [] {:regexp #"."}) => (throws Exception "Required options #{:quality} for transform-images are missing."))

(fact
 "What if it's not an image? Well, we can detect text assets at least."

 (with-tmp-dir
   (transform-images [{:path "/stuff.css" :contents ""}]
                     {:tmp-dir tmp-dir
                      :regexp #".+"
                      :quality 0.2})) => (throws Exception "Your transform-images regexp matches a non-binary asset: /stuff.css"))
