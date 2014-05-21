(ns erdos.inspector
  "More advanced inspector functions for clojure data structures.
   For experimentation, use the erdos.inspector/inspect function."
  (:import
   (java.awt.event MouseAdapter)
   (javax.swing JScrollPane JFrame)))


                                        ; CLOJURE
(defn toggle-contains
  [coll x]
  (if (contains? coll x)
    (disj coll x) (conj coll x)))

                                        ; SWING

(defn- repaint!
  [component]
  (doto (.getParent component)
    .revalidate
    .repaint))

(def icon!
  (memoize
   (fn [name]
     (->> name str
          clojure.java.io/resource
          javax.swing.ImageIcon.))))

(defn- graphics-smooth
  [g]
  (doto (.create g)
    (.setRenderingHint
     java.awt.RenderingHints/KEY_ANTIALIASING
     java.awt.RenderingHints/VALUE_ANTIALIAS_ON)))

(defn- draw-string [g s x y vertical horizontal]
  {:pre [(#{:left :center :right} horizontal)
         (#{:top :middle :bottom} vertical)
         (string? s)
         (instance? java.awt.Graphics g)
         (number? x) (number? y)]}
  (let [fm (.getFontMetrics g)
        sb (.getStringBounds fm s g)
        th (.getHeight sb)
        x  (case horizontal
             :left x
             :right (- x (.getWidth sb))
             :center (+ x (/ (.getWidth sb) 2)))
        y (case vertical
            :middle (+ y (.getAscent fm) (- (/ (.getHeight sb) 2))))]
    (.drawString g (str s) (int x) (int y))))

                                        ; MODEL

(def ^:private obj-coll-children nil)
(defmulti ^:private obj-coll-children class)

(defmethod obj-coll-children clojure.lang.IRef [x] [@x])

(defmethod obj-coll-children clojure.lang.LazySeq
  [x]
  (if (realized? x)
    (if-not (seq x) []
            (cons (first x) (lazy-seq (obj-coll-children (rest x)))))
    [::pending]))

(defmethod obj-coll-children clojure.lang.ISeq [x]
  (cons (first x) (lazy-seq (obj-coll-children (rest x)))))

(prefer-method obj-coll-children clojure.lang.IPending clojure.lang.ISeq)
(prefer-method obj-coll-children clojure.lang.IPending ::seq)

(defmethod obj-coll-children clojure.lang.PersistentList$EmptyList [_] [])

(defmethod obj-coll-children java.util.Map$Entry [e]
  [(.getKey e) (.getValue e)])

(defmethod obj-coll-children java.lang.Throwable [e]
  (-> e bean seq flatten))

(prefer-method obj-coll-children java.util.Map$Entry ::seq)

(derive clojure.lang.ASeq ::seq)
(derive clojure.lang.Seqable ::seq)
(derive java.lang.Iterable ::seq)
(derive java.util.Map ::seq)
(derive clojure.lang.IObj ::clj)

(doseq [f [object-array int-array float-array long-array
           double-array byte-array char-array short-array boolean-array]]
  (derive (class (f 0)) ::seq))

(defmethod obj-coll-children ::seq [m] (seq m))

(defn- iref?
  [x] (instance? clojure.lang.IRef x))

(defn- obj-state?
  "Returns true if x contains state that may change over time."
  [x]
  (or (iref? x)))

(defn- obj-state-children
  "Returns coll of thildren objs of x iff obj-state? is true."
  [x] [@x])

(defn- obj-has-children? [x]
  (or (not (nil? (get-method obj-coll-children (class x))))
      (obj-state? x)
      (and (not (nil? x)) (-> x .getClass .isArray))))

(defn- obj-children
  [x]
  (or
   (if-let [m (get-method obj-coll-children (class x))]
     (m x))
   (if (iref? x)
     (obj-state-children x))
   (if (-> x .getClass .isArray)
     (seq x))))

(def ^:private paint-x1 48)
(def ^:private paint-height 42)

(def ^:private font-normal
  (new java.awt.Font "SansSerif" java.awt.Font/PLAIN 12))

(def ^:private font-underlined
  (.deriveFont
   font-normal
   (new java.util.HashMap
        {java.awt.font.TextAttribute/UNDERLINE
         java.awt.font.TextAttribute/UNDERLINE_ON})))

(def ^:private font-bold
  (.deriveFont
   font-normal
   (new java.util.HashMap
        {java.awt.font.TextAttribute/WEIGHT
         java.awt.font.TextAttribute/WEIGHT_HEAVY})))

(def ^:private color
  (memoize
   (fn ([k] ({:white java.awt.Color/WHITE
              :black java.awt.Color/BLACK} k))
     ([r g b] (new java.awt.Color r g b)))))

(defn obj-type-str
  "Returns a human-readable string representatin of the type of the object."
  [obj]
  (if-not  (nil? obj)
    (let [s (-> obj class .getName)]
      (cond
       (.startsWith s "java.lang.")    (subs s 10)
       (.startsWith s "clojure.lang.") (subs s 13)
       :else s)) ""))

(defn- paint-row-leaf
  [cmp g data ind level hover]
  (let [x-text (+ paint-x1 (* level paint-x1))
        y-text (/ paint-height 2)
        draw (fn [s [red green blue]]
               (.setColor g (color red green blue))
               (draw-string g (str s) x-text y-text :middle :left))]
    (when-not (or (nil? data) (= ::pending data))
      (.setFont g font-bold)
      (.setColor g (color 220 200 200))
      (draw-string g (obj-type-str data)
                   (- (.getWidth cmp) 12) y-text
                   :middle :right)
      (.setFont g font-normal))
    (cond
     (= ::pending data)
     (do
       (.setFont g font-underlined)
       (draw "pending..." [12 12 255])
       (.setFont g font-normal))
     (= nil data)       (draw "nil" [100 100 100])
     (string? data)     (draw (str \" data \") [184 12 32])
     (keyword? data)    (draw (str data) [243 150 23])
     (instance? java.util.regex.Pattern data)
       (draw (str \# \" data \") [232 32 154])
     :else              (draw (str data) [3 3 3]))))

(defn- row-branch-icon
  [data]
  (cond
   (nil? data) nil
   (vector? data) (icon! "clj-vector.png")
   (seq? data)    (icon! "clj-list.png")
   (map? data)    (icon! "clj-map.png")
   (set? data)    (icon! "clj-set.png")
   (iref? data)
   (icon! "clj-iref.png")
   (.isArray (type data)) (icon! "java-array.png")
   :else          (icon! "java-obj.png")))

(defn- paint-row-branch
  [g data ind level hover]
  (let [x  (* level paint-x1)
        t (obj-type-str data)]
    (.setColor g java.awt.Color/BLACK)
    (draw-string g t (+ paint-x1 x) (/ paint-height 2) :middle :left)
    (if-let [icon (row-branch-icon data)]
      (.paintIcon icon nil g (+ 4 x) 4))))

(defn- data-clickable?
  [data]
  (or (obj-has-children? data) (= data ::pending)))

(defn- paint-row
  [cmp g data ind level hover]
  (let [w (-> g .getClipBounds .getWidth)
        x0 (-> g .getClipBounds .getX)
        g2 (.create g)]
    (.setColor  g2 (color 160 160 160))
    (.setStroke g2
                (new java.awt.BasicStroke (float 1)
                     java.awt.BasicStroke/CAP_BUTT
                     java.awt.BasicStroke/JOIN_ROUND
                     1 (float-array [2 4]) 0))
    (doseq [l (range 0 level)]
      (let [x (+ 20 (* l paint-x1))]
        (.drawLine g2 x 0 x paint-height)))
    (when (and hover (data-clickable? data))
      (doto g ; bg strip
        (.setColor (color 240 240 240))
        (.fillRect x0 0 w paint-height)
        (.setColor (color 220 220 220))
        (.drawLine x0 0 (+ x0 w) 0)
        (.drawLine x0 (dec paint-height) (+ x0 w) (dec paint-height))))
    (if (obj-has-children? data)
      (paint-row-branch g data ind level hover)
      (paint-row-leaf   cmp g data ind level hover))))

(defn- rows-opened
  ([data omap pref]
     (let [cx (cons data pref)
           ff (fn [x i] (rows-opened x omap (cons i pref)))]
       (if (and (obj-has-children? data) (contains? omap pref))
         (cons cx (mapcat ff (obj-children data) (range)))
         [cx])))
  ([data omap]
     (rows-opened data omap nil)))

(defn- path->obj [root path]
  (if-not (seq path) root
          (recur (nth (seq root) (first path)) (next path))))

(defn tree-component
  [data]
  "Creates a java swing JComponent instance to show structure of data object.
   The result of this function call may be used as a gui element in custom swing applications."
  (let [hover-ind   (atom nil) ;; currently hovered line
        user-open   (atom ::undefined) ;; generated by user clicks.
        datac       (atom nil)
        data-cache  (fn [] (deref datac))
        swing-component (promise)
        recalc-cache    (fn [opened] (reset! datac (vec (rows-opened data opened))))
        cache-pa        (atom {})]
    (add-watch hover-ind :hover (fn [_ _ _ i] (.repaint @swing-component)))
    (add-watch user-open :ch #(recalc-cache %4))
    (add-watch datac     :-P
               (fn [k r old neu]
                 (let [open @user-open
                       decons (fn [[obj & path]] (if (and (iref? obj) (open path)) [path obj]))
                       neu-map (into {} (keep decons neu))]
                   (reset! cache-pa neu-map)
                   (when (realized? swing-component)
                     (.repaint @swing-component)))))
    (add-watch cache-pa  :-D
               (fn [_ _ old neu]
                 (let [listener (fn [_ _ _ _] (recalc-cache @user-open))]
                   (doseq [q (map old (remove neu (keys old)))]
                     (remove-watch q ::change))
                   (doseq [q (map neu (remove old (keys neu)))]
                     (add-watch q ::change listener)))))
    (reset! user-open #{nil})
    (doto
        (proxy [javax.swing.JPanel] []
          (getMinimumSize []
            (new java.awt.Dimension
                 240 (* (count (data-cache)) paint-height)))
          (getPreferredSize [] (.getMinimumSize this))
          (paintComponent [g]
            (let [g (graphics-smooth g)
                  b (.getClipBounds g)
                  m (int (/ (.getY b) paint-height))
                  n (+ 2 (int (/ (.getHeight b) paint-height)))
                  hi  @hover-ind
                  dc (data-cache)]
              (assert (vector? dc)) ; we need fast random access.
              (.setFont g font-normal)
              (.translate g 0 (* m paint-height))
              (doseq [i (range m (+ m n))] ;; far not perfect
                (when-let [x (nth dc i nil)]
                  (paint-row this g (first x) i (-> x count dec) (= i hi)))
                (.translate g 0 paint-height))))
          (getHeight [] (* paint-height (count (data-cache)))))
      (.setBorder nil)
      (.setBackground (color :white))
      (.setOpaque false)
      (.addMouseMotionListener
       (proxy [MouseAdapter] []
         (mouseMoved [e] (reset! hover-ind (-> e .getY (/ paint-height) int)))))
      (.addAncestorListener
       (proxy [javax.swing.event.AncestorListener] []
         (ancestorAdded [e] nil)
         (ancestorMoved [e] nil)
         (ancestorRemoved [e] (reset! user-open #{}))))
      (.addMouseListener
       (proxy [MouseAdapter] []
         (mousePressed [e]
           (when-let [ind @hover-ind]
             (when-let [x (nth (data-cache) ind nil)]
               (cond ;; order is pretty important here.
                (obj-has-children? (first x))
                (swap! user-open toggle-contains (next x))
                (= (first x) ::pending)
                (let [parent-path (-> x next next)
                      obj  (path->obj data parent-path)]
                  ;; TODO it is ugly, you can do it better!
                  (nth obj (dec (count (obj-children obj))) nil)
                  (swap! user-open identity)
                  (swap! hover-ind inc))) ;; TODO i do feel bad.
               (repaint! @swing-component))))))
      (->> (deliver swing-component)))))

(defn inspect
  "Displays a new pop-up window showing the tree-structure of data object."
  [data]
  (doto (JFrame. "Clojure Inspector")
    (.add (doto (JScrollPane. (tree-component data))
            (.setBorder nil)))
    (.setSize 400 600)
    (.setVisible true)))

:ok
