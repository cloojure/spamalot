(ns spamalot.core
  "Contains the core functions for namespace `spamalot.core`."
  (:use tupelo.core)
  (:require
    [clojure.spec.alpha :as sp]
    [clojure.spec.test.alpha :as stest]
    [clojure.spec.gen.alpha :as gen]
    [schema.core :as s]
    [tupelo.schema :as tsk]
  ))

(def email-domains
  #{"indeediot.com"
    "monstrous.com"
    "linkedarkpattern.com"
    "dired.com"
    "lice.com"
    "careershiller.com"
    "glassbore.com"})

(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")

(sp/def ::email-address
  (sp/with-gen
    (sp/and string? #(re-matches email-regex %))
    #(->>
       (gen/tuple (gen/such-that not-empty (gen/string-alphanumeric))
         (sp/gen email-domains))
       (gen/fmap (fn [[addr domain]] (str addr "@" domain))))))

(sp/def ::spam-score (sp/double-in :min 0 :max 1))

(sp/def ::email-record (sp/keys :req-un [::email-address ::spam-score]))

(defn gen-emails [num]
  (vec (gen/sample (sp/gen ::email-record) num)))

;-----------------------------------------------------------------------------
(def Email {:email-address s/Str
            :spam-score    s/Num})

(def emails-seen (atom nil)) ; #todo how to tell Schema type = (atom {s/Str tsk/Map} )  ???

(defn email-seen-reset! [] (reset! emails-seen {}))
(email-seen-reset!)

(s/defn record-email
  [email :- Email]
  (swap! emails-seen assoc (grab :email-address email) email))

(s/defn seen-email? :- s/Bool
  [email :- Email]
  (contains-key? @emails-seen (grab :email-address email)))

;-----------------------------------------------------------------------------
(def max-spam-score 0.3)
(s/defn non-spammy-email :- s/Bool
  [email :- Email ]
  (<= (grab :spam-score email) max-spam-score))

;-----------------------------------------------------------------------------
(def CumStats {:cum-spam-score s/Num
               :cum-num-emails s/Int})
(def cum-spam-score-max 0.05)
(def cum-stats-atom (atom nil))

(s/defn cum-stats-reset! :- CumStats
  [] (reset! cum-stats-atom {:cum-spam-score 0.0 :cum-num-emails 0}))
(cum-stats-reset!)

(s/defn update-cum-stats
  [cum-stats-curr :- CumStats
   email          :- Email]
  (it-> cum-stats-curr
    (update it :cum-spam-score + (grab :spam-score email))
    (update it :cum-num-emails inc)))

(s/defn new-email-ok-cum-stats? :- s/Bool
  [email :- Email]
  (let [cum-stats-new      (update-cum-stats @cum-stats-atom email)
        cum-spam-score-new (/ (grab :cum-spam-score cum-stats-new)
                             (grab :cum-num-emails cum-stats-new))]
    (<= cum-spam-score-new cum-spam-score-max)))

(s/defn accumulate-email-stats
  [email :- Email]
  (swap! cum-stats-atom update-cum-stats email))

