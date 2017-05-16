(ns wordbots.memebot.image
  (:require [clojure.string :as string])
  (:import [java.io ByteArrayOutputStream]
           [java.awt.image BufferedImage]
           [java.awt Font FontMetrics Color Graphics]
           [java.awt.geom Rectangle2D]))

;; Defaults
(def ^:dynamic *max-font-size* 48)
(def ^:dynamic *bottom-margin* 10)
(def ^:dynamic *top-margin* 5)
(def ^:dynamic *side-margin* 10)

(defn- split-with* [f coll]
  (loop [a []
         b coll]
    (let [item (first b)]
      (if (and item (f a item))
        (recur (conj a item) (rest b))
        [a b]))))

(defn- width-check [g width a b]
  (<= (.. g
          getFontMetrics
          (getStringBounds (string/join " " (conj a b)) g)
          getWidth)
      width))

(defn- split-string [g text max-line-width]
  (loop [lines []
         words (string/split text #"\s")]
    (let [[l w] (split-with* (partial width-check g max-line-width) words)]
      (if (empty? l)
        (if (empty? w)
          lines
          (conj lines (string/join " " w)))
        (recur (conj lines (string/join " " l)) w)))))

(defn- calculate-size [g text]
  (reduce
   (fn [[h maxWidth] line]
     (let [bounds (.. g getFontMetrics (getStringBounds line g))]
       [(+ h (.getHeight bounds))
        (max maxWidth (Math/ceil (.getWidth bounds)))]))
   [0 0]
   (string/split-lines text)))

(defn calculate-text
  ([g text image] (calculate-text g text image *side-margin* *max-font-size*))
  ([g text image side-margin max-font-size]
   (let [max-caption-height (/ (.getHeight image) 5) ;; 1/5th image height
         max-line-width (- (.getWidth image) (* *side-margin* 2))]
     (loop [fontsize max-font-size]
       (.setFont g (Font. "Arial" Font/BOLD fontsize))
       (let [formatted (string/join "\n" (split-string g text max-line-width))
             [h linewidth] (calculate-size g formatted)]
         (if (and (<= h max-caption-height) (<= linewidth max-line-width))
           {:fontsize fontsize
            :height (int h)
            :formattedtext formatted}
           (recur (dec fontsize))))))))

(defn- draw-string-centered* [^Graphics g image y line]
  (let [bounds (.. g getFontMetrics (getStringBounds line g))
        x (int (/ (- (.getWidth image) (.getWidth bounds)) 2))]
    (doto g
      (.setColor Color/BLACK)
      (.drawString line (+ x 2) (+ y 2))
      (.setColor Color/WHITE)
      (.drawString line x y))
    (+ y (.. g getFontMetrics getHeight))))

(defn draw-string-centered
  ([g text image top]
   (let [{:keys [formattedtext fontsize height]} (calculate-text g text image)]
     (draw-string-centered g formattedtext image top height fontsize *top-margin* *bottom-margin*)))
  ([^Graphics g text image top height fontsize top-margin bottom-margin]
   (let [y (if top
             (+ top-margin (-> g (.getFontMetrics) (.getHeight)))
             (+ (-> g (.getFontMetrics) (.getHeight))
                (- (.getHeight image) height bottom-margin)))]
     (.setFont g (Font. "Arial" Font/BOLD fontsize))
     (reduce (partial draw-string-centered* g image)
             y
             (string/split-lines text)))))

(defn overlay-text
  "Given a BufferedImage, and optional top and bottom captions,
  returns the image with the text overlayed."
  [^BufferedImage image top-caption bottom-caption]
  (let [g (.getGraphics image)]
    (.setRenderingHint g
                       java.awt.RenderingHints/KEY_TEXT_ANTIALIASING
                       java.awt.RenderingHints/VALUE_TEXT_ANTIALIAS_GASP)
    (when top-caption (draw-string-centered g top-caption image true))
    (when bottom-caption (draw-string-centered g bottom-caption image false))
    image))
