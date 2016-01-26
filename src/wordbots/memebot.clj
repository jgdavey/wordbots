(ns wordbots.memebot
  (:require [wordbots.memebot.image :as img]
            [wordbots.protocols :as p]
            [wordbots.startupbot :as startupbot]
            [clojure.java.io :as io]
            [image-resizer.core :as resizer]
            [clojure.string :as str])
  (:import [clojure.java.io IOFactory]
           [javax.imageio ImageIO]
           [java.util UUID]
           [java.net URI URL]
           [java.io File ByteArrayOutputStream ByteArrayInputStream]
           [java.awt.image BufferedImage]))

(def ^:dynamic *max-width* 400)
(def ^:dynamic *max-height* 500)
(def ^:dynamic *format* "jpg")

(defprotocol ToImage
  (to-image [this]))

(extend BufferedImage
  ToImage
  {:to-image (fn [this] this)})

(def ^:private io-to-image
  {:to-image (fn [readable]
               (-> readable
                   io/as-url
                   ImageIO/read))})

(extend String ToImage io-to-image)
(extend URL ToImage io-to-image)
(extend URI ToImage io-to-image)
(extend File ToImage io-to-image)

(defn render-image ^BufferedImage [imageable top-caption bottom-caption]
  (let [image (-> imageable
                  to-image
                  (resizer/resize *max-width* *max-height*))]
    (img/overlay-text image top-caption bottom-caption)))

;; Returns input stream
(defn to-stream [^BufferedImage image]
  (with-open [baos (ByteArrayOutputStream.)]
    (ImageIO/write image *format* baos)
    (ByteArrayInputStream. (.toByteArray baos))))

(defn save-image [output-root ^BufferedImage image]
  (let [relative-path (str (UUID/randomUUID) "." *format*)]
    (with-open [file (io/output-stream (str output-root "/" relative-path))]
      (ImageIO/write image *format* file))
    relative-path))

(defn generate* [{:keys [request root-path top-caption bottom-caption images]}]
  (let [prefix (str (name (:scheme request))
                    "://"
                    (get-in request [:headers "host"])
                    "/")]
    (->> (render-image (rand-nth images) top-caption bottom-caption)
         (save-image root-path)
         (str prefix))))

(defn bot [root textbot images]
  (reify
    p/Bot
    (init [_] (p/init textbot))
    (generate [_ req]
      (generate* {:request req
                  :root-path root
                  :images images
                  :top-caption (p/generate textbot req)
                  :bottom-caption (p/generate textbot req)}))))

(def ^:private team-photos
  (mapv (partial str "https://hashrocket-production.s3.amazonaws.com/uploads/rocketeer/profile_image")
        ["/11/josh-davey.jpg"
         "/14/marian-phelan.jpg"
         "/27/amy-mcillwain.jpg"
         "/3/cameron-daigle.jpg"
         "/33/diana-mccann.jpg"
         "/35/matt-polito.jpg"
         "/36/brian-dunn.jpg"
         "/37/micah-cooper.jpg"
         "/4/chris-cardello.jpg"
         "/43/taylor-mock.jpg"
         "/44/rye-mason.jpg"
         "/45/jack-christensen.jpg"
         "/46/thais-camilo.jpg"
         "/47/gabriel-reis.jpg"
         "/5/daniel-ariza.jpg"
         "/75/andrew-dennis.jpg"
         "/76/jonathan-jackson.jpg"
         "/78/micah-woods.jpg"
         "/82/chase-mccarthy.jpg"
         "/84/chris-erin.jpg"
         "/85/carty-seay.jpg"
         "/86/jake-worth.jpg"
         "/87/mike-fretto.jpg"
         "/88/josh-branchaud.jpg"
         "/89/chad-brading.jpg"
         "/91/dorian-karter.jpg"
         "/93/jason-cummings.jpg"
         "/94/dillon-hafer.jpg"
         "/95/nick-palaniuk.jpg"]))

(defn startup-image-bot [root]
  (let [bot (startupbot/bot)]
    (reify
      p/Bot
      (init [_] (p/init bot))
      (generate [_ req]
        (generate* {:request req
                    :root-path root
                    :images team-photos
                    :top-caption "My Startup?"
                    :bottom-caption (p/generate bot req)})))))
