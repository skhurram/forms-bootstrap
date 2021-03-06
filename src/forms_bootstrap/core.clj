(ns forms-bootstrap.core
  (:use net.cgrand.enlive-html
        forms-bootstrap.util)
  (:require [forms-bootstrap.validation :refer [if-valid]]
            [clojure.string :as string]
            [noir.session :as session]
            [noir.response :as response]))

;;Considerations:
;;1)form type: horizontal, stacked, or inline
;;if stacked, labels are above fields, which have width 100%
;;if horizontal, labels get 25% and field gets 75%
;;;;;--- should be able to provide label-width, field-width
;;;;;--- for inline elements, need label width, and each field width
                                        ;if inline, not supported yet.

;;input-field, text-area-field, select-field, file-input are ALL the
;;same, abstract out this logic in a macro.

;;to do: checkbox / radio, inline.

(def form-template "forms_bootstrap/horizontal-template.html")

;; ## HELPER FUNCTIONS
(defn to-html
  "Takes in nodes and emits HTML"
  [nodes]
  (println (apply str (emit* nodes))))

(defn handle-error-css
  "Used within a snippet to add 'error' to the class of an html element, if appropriate."
  [errors]
  (if (peek errors)
    (add-class "has-error")
    identity))

(defn span
  "Creates an enlive node map for an html span element. Type should be
  'help-block' for errors."
  [type content]
  {:tag "span"
   :attrs {:class type}
   :content content})

(defn add-spans
  "Used by enlive snippets to add a span to display errors (if
  there are any). If there are no errors then it adds block help messages."
  [errors help-block]
  (if (or (peek errors) help-block)
    (append (span "help-block" (or (first errors) help-block)))
    identity))

(defn set-value
  "Sets the value key of the given element to either :value (passed in
  the node), or :default (from a previous form submission saved in the
  flash)."
  [value default]
  (if (or value default)
    (set-attr :value (or value default))
    identity))

(defn maybe-disable
  [disabled]
  (if (= disabled true)
    (set-attr :disabled "")
    identity))

(defn maybe-set-attrs
  "Takes in a map of k/v pairs of attributes, such as name, class, value, etc,
  and sets the non-empty attributes."
  [kvs]
  (apply do->
         (map (fn [[attr val]] (if-seq val #(set-attr attr %)))
              kvs)))

;; TODO: If we want to get fancy, we can use a multimethod on the
;; glyph input to customize what we do with the string, rather than
;; assuming it's the name of a bootstrap glyph.
(defn input-glyph
  "If the glyph is present, prepends it to the supplied input."
  [glyph]
  (if glyph
    (do-> (wrap :div {:class "input-group"})
          (prepend (html [:span {:class "input-group-addon"}
                          [:span {:class (str "glyphicon glyphicon-" glyph)}]])))
    identity))

(defn button-glyph
  "If the glyph is present, prepends it to the supplied button."
  [glyph]
  (if glyph
    (prepend (html [:span {:class (str "glyphicon glyphicon-" glyph)}]))
    identity))

;; ## SNIPPETS

;;Grabs the whole form from the template, sets the action, replaces
;;the entire contents with fields, and appends a submit button.
;;Fields here is a sequence of stuff generated by all of the
;;defsnippets below, one for each form element. Form-attrs is a map of
;;key value pairs of attributes.
(defsnippet basic-form
  form-template
  [:form]
  [{:keys [action fields submitter class enctype legend method form-attrs]
    :or {class "form-horizontal"
         method "post"}}]
  [:form] (do-> (maybe-set-attrs (merge {:action action :class class :method method}
                                        form-attrs))
                (if-seq enctype #(set-attr :enctype %))
                (content (if (seq legend)
                           [{:tag :legend
                             :content legend}
                            fields]
                           fields))
                (append submitter)))

;;Creates a hidden input field
(defsnippet hidden-input
  form-template
  [:div.hidden-field :input]
  [{:keys [id class name default disabled value glyph]
    :or {default ""}}]
  [:input] (do->
            (input-glyph glyph)
            (maybe-set-attrs {:name name :class class :id id})
            (set-value value default)
            (maybe-disable disabled)))

;; ex: [:name "namehere" :label "labelhere" :type "text"
;;      :size "xlarge" :errors ["some error"] :default "john"]
;;Custom-attrs is a map of key/value pairs of input attrs
;;div-attrs is a map of key/value pairs of the outer control-size div attrs
(defsnippet input-lite
  form-template
  [:div.input-field :input]
  [{:keys [id name class type default disabled placeholder value
           onclick style custom-attrs glyph div-attrs]}]
  [:input] (do->
            (maybe-set-attrs (merge {:name name :type type :class class :style style
                                     :placeholder placeholder :id id :onclick onclick}
                                    custom-attrs))
            (set-value value default)
            (maybe-disable disabled)
            (input-glyph glyph)
            (if (map? div-attrs)
              (wrap :div div-attrs)
              identity)))

;;label-size and control-size must add up to 12.
;;form-group-style is for the outer div (.formgroup .inputfield)
;;hidden appends "display:none" to form-group-style
(defsnippet input-field
  form-template
  [:div.input-field]
  [{:keys [hidden name label errors help-block label-size control-size
           form-group-style]
    :or {label-size 3
         control-size 9}
    :as m}]
  [:div.input-field] (do-> (maybe-set-attrs {:style (if hidden
                                                      (str "display:none;" form-group-style)
                                                      form-group-style)})
                           (handle-error-css errors))
  [:label] (do-> (content label)
                 (set-attr :class (str "control-label col-sm-" label-size)
                           :for name))
  [:div.control-size] (do-> (set-attr :class (str "control-size col-sm-" control-size))
                            (add-spans errors help-block))
  [:input] (substitute (input-lite m)))

;;ex: [:name "namehere" :label "labelhere" :type "text-area"
;;     :size "xlarge" :rows "3" :default "defaultstuff"]
;;custom attrs is a map of key value pairs
(defsnippet text-area-lite
  form-template
  [:div.text-area :textarea]
  [{:keys [name label rows errors value default class style custom-attrs id div-attrs]
    :or {rows "3"}}]
  [:textarea] (do-> (maybe-set-attrs (merge
                                      {:class class :style style :name name :rows (str rows)
                                       :id id}
                                      custom-attrs))
                    (set-value value default)
                    (content default)
                    (if (map? div-attrs)
                      (wrap :div div-attrs)
                      identity)))

(defsnippet text-area-field
  form-template
  [:div.text-area]
  [{:keys [hidden name label errors help-block label-size control-size form-group-style]
    :or {label-size 3
         control-size 9}
    :as m}]
  [:div.text-area] (do-> (maybe-set-attrs {:style (if hidden
                                                    (str "display:none;" form-group-style)
                                                    form-group-style)})
                         (handle-error-css errors))
  [:label] (do-> (content label)
                 (set-attr :class (str "control-label col-sm-" label-size)
                           :for name))
  [:div.control-size] (do-> (set-attr :class (str "control-size col-sm-" control-size))
                            (add-spans errors help-block))
  [:textarea] (substitute (text-area-lite m)))

;;Creates a select (dropdown) form element with the given values
;;Ex: {:type "select" :name "cars" :size "xlarge" :label "Cars"
;;     :inputs [["volvo" "Volvo"] ["honda" "Honda"]]}
;;inputs are 2 tuples of [value value-label]
;;custom inputs are [label {:value "" :anyotherattr ""}]
(defsnippet select-lite
  form-template
  [:div.select-dropdown :select]
  [{:keys [name style class label inputs custom-inputs default type custom-attrs id div-attrs]}]
  [:select] (do->
             (maybe-set-attrs (merge {:name name :id id :style style :class class}
                                     custom-attrs))
             (if (string-contains? type "multiple")
               (set-attr :multiple "multiple")
               identity)
             (if (map? div-attrs)
               (wrap :div div-attrs)
               identity))
  [:option] (if custom-inputs
              (clone-for [[label {:keys [value] :as attrs}] custom-inputs]
                         (do-> #(assoc % :attrs attrs)
                               (if (= default value)
                                 (set-attr :selected "selected")
                                 identity)
                               (content label)))
              (clone-for [[value value-label] inputs]
                         (do-> (set-attr :value value)
                               (if (= default value)
                                 (set-attr :selected "selected")
                                 identity)
                               (content value-label)))))

(defsnippet select-field
  form-template
  [:div.select-dropdown]
  [{:keys [hidden name label errors help-block label-size control-size form-group-style]
    :or {label-size 3
         control-size 9}
    :as m}]
  [:div.select-dropdown] (do-> (maybe-set-attrs {:style (if hidden
                                                          (str "display:none;" form-group-style)
                                                          form-group-style)})
                               (handle-error-css errors))
  [:label] (do-> (content label)
                 (set-attr :class (str "control-label col-sm-" label-size)
                           :for name))
  [:div.control-size]  (do-> (set-attr :class (str "control-size col-sm-" control-size))
                             (add-spans errors help-block))
  [:select] (substitute (select-lite m)))

;; ex: {:type "select" :name "cars" :size "xlarge" :label "Cars"
;;      :inputs [["volvo" "Volvo"] ["honda" "Honda"]]}
;;custom-inputs format: [["OptionLabelOne" {:class "first" :value
;;"one"}]]
;;TO DO: clean this up! issue is custom-inputs vs inputs
(defsnippet checkbox-or-radio-lite
  form-template
  [:div.checkbox-or-radio.inline :div.control-size :label]
  [{:keys [name inputs custom-inputs type default style custom-attrs]}]
  [:label] (if custom-inputs
             (clone-for [[value-label {:keys [value] :as attrs}] custom-inputs]
                        [:label] (if (string-contains? type "inline")
                                   (set-attr :class (str (first-word type) "-inline"))
                                   (do-> (remove-attr :class)
                                         (wrap :div {:class type})))
                        [:input] (do-> (maybe-set-attrs (merge attrs
                                                               custom-attrs
                                                               {:type (first-word type)
                                                                :name name
                                                                :style style
                                                                :id (remove-spaces
                                                                     value)}))
                                       (content value-label)
                                       (if (contains?
                                            (set (collectify default)) value)
                                         (set-attr :checked "checked")
                                         identity)))
             (clone-for [[value value-label] inputs]
                        [:label] (if (string-contains? type "inline")
                                   (set-attr :class (str (first-word type) "-inline"))
                                   (do-> (remove-attr :class)
                                         (wrap :div {:class type})))
                        [:input] (do-> (maybe-set-attrs {:type (first-word type)
                                                         :name name
                                                         :style style
                                                         :value value})
                                       (content value-label)
                                       (if (contains?
                                            (set (collectify default)) value)
                                         (set-attr :checked "checked")
                                         identity)))))

(defsnippet checkbox-or-radio
  form-template
  [:.checkbox-or-radio.inline]
  [{:keys [hidden class name label errors help-block label-size control-size form-group-style]
    :or {label-size 3
         control-size 9}
    :as m}]
  [:div.checkbox-or-radio]  (do-> (maybe-set-attrs {:style
                                                    (if hidden
                                                      (str "display:none;" form-group-style)
                                                      form-group-style)})
                                  (handle-error-css errors)
                                  (add-class class))
  [:label.control-label] (do-> (content label)
                               (set-attr :class (str "control-label col-sm-" label-size)
                                         :name name))
  [:div.control-size] (do-> (content (checkbox-or-radio-lite m))
                            (set-attr :class (str "control-size col-sm-" control-size))
                            (add-spans errors help-block)))

;;Creates a file input button custom-attrs is a map of kv pairs
(defsnippet file-input-lite
  form-template
  [:div.file-input :input]
  [{:keys [name style custom-attrs]}]
  [:input] (maybe-set-attrs (merge {:name name :style style}
                                   custom-attrs)))

(defsnippet file-input
  form-template
  [:div.file-input]
  [{:keys [hidden name label errors help-block label-size control-size form-group-style]
    :as m
    :or {label-size 3
         control-size 9}}]
  [:div.file-input] (do-> (maybe-set-attrs {:style
                                            (if hidden
                                              (str "display:none;" form-group-style)
                                              form-group-style)})
                          (handle-error-css errors)
                          (add-class class))
  [:label] (do-> (content label)
                 (set-attr :class (str "control-label col-sm-" label-size)
                           :for name))
  [:div.control-size]  (do-> (set-attr :class (str "control-size col-sm-" control-size))
                             (add-spans errors help-block))
  [:input] (substitute (file-input-lite m)))

;;Creates a submit button with a specified label (value)
(defsnippet button-lite
  form-template
  [:div.submit-button :.btn-primary]
  [{:keys [text class name button-attrs type div-attrs glyph]
    :or {class "btn btn-default"}}]
  [:.btn-primary] (do-> (content text)
                        (maybe-set-attrs (merge {:class class
                                                 :name name
                                                 :type type}
                                                button-attrs))
                        (button-glyph glyph)
                        (if (map? div-attrs)
                          (wrap :div div-attrs)
                          identity)))

;;'grid' is used for the control size, specifies size + offset.
(defsnippet button-field
  form-template
  [:div.submit-button]
  [{:keys [grid hidden form-group-style] :as m
    :or {grid "col-sm-offset-3 col-sm-9"}}]
  [:.submit-button] (maybe-set-attrs {:style
                                      (if hidden
                                        (str "display:none;" form-group-style)
                                        form-group-style)})
  [:div.control-size]  (set-attr :class (str "control-size " grid))
  [:.btn-primary] (substitute (button-lite m)))

(defsnippet make-submit-button
  form-template
  [:div.submit-button]
  [label cancel-link button-attrs]
  [:.btn-primary] (substitute (button-lite {:text label
                                            :type "submit"
                                            :class "btn btn-primary"
                                            :button-attrs button-attrs}))
  [:a.cancel-link] (if cancel-link
                     (if (= cancel-link "modal")
                       (set-attr :data-dismiss "modal")
                       (set-attr :href cancel-link))
                     (content "")))

;;HELPERS
(defn make-field-helper
  "Used by make-field, calls the right defsnippet (either the lite
  version in a single 'controls' div, or the full version wrapped in a
  'form-group')"
  [form-class field field-lite m]
  (if (string-contains? form-class "form-inline")
    (list (field-lite m) " ")
    (field m)))

;;Inline fields is A label and a collection of lite controls, each
;;wrapped in a control-size div.
;;columns contains the user specified field maps
;;Inline-content is a collection of all the lite controls (so mapping
;;make-field over columns)
(defsnippet inline-fields
  form-template
  [:div.inline-fields]
  [{:keys [label columns inline-content hidden help-block label-size form-group-style]
    :or {label-size 3}}]
  [:div.inline-fields] (do-> (maybe-set-attrs {:style
                                               (if hidden
                                                 (str "display:none;" form-group-style)
                                                 form-group-style)})
                             (handle-error-css (some (fn[a] a) (map :errors columns))))
  [:label.control-label] (do-> (content label)
                               (set-attr :class (str "control-label col-sm-" label-size)))
  [:div.controls-row] (content (interpose " " inline-content))
  [[:div.control-size first-of-type]] (add-spans [(some (fn[a] a) (map :errors columns))]
                                                 help-block))

(defmulti make-field
  "Takes a single map representing a form element's attributes and
  routes it to the correct snippet based on its type. Supports input
  fields, text areas, dropdown menus, checkboxes, radios, and file
  inputs. Ex: {:type 'text' :name 'username' :label 'Username' :errors
  ['Incorrect username'] :default ''}"
  (fn [form-class m]
    (first-word (:type m))))

(defmethod make-field "text"
  [form-class m]
  (make-field-helper form-class input-field input-lite m))

(defmethod make-field "hidden"
  [form-class m]
  (hidden-input m))

(defmethod make-field "password"
  [form-class m]
  (input-field (dissoc m :default)))

(defmethod make-field "button"
  [form-class m]
  (make-field-helper form-class button-field button-lite m))

(defmethod make-field "text-area"
  [form-class m]
  (make-field-helper form-class text-area-field text-area-lite m))

(defmethod make-field "select"
  [form-class m]
  (make-field-helper form-class select-field select-lite m))

(defmethod make-field "radio"
  [form-class m]
  (make-field-helper form-class checkbox-or-radio checkbox-or-radio-lite m))

(defmethod make-field "checkbox"
  [form-class m]
  (make-field-helper form-class checkbox-or-radio checkbox-or-radio-lite m))

(defmethod make-field "inline-fields"
  [form-class m]
  (inline-fields
   (assoc m
     :inline-content (map #(make-field "form-inline" %)
                          (:columns m))) ))

(defmethod make-field "custom"
  [form-class m]
  (:html-nodes m))

(defmethod make-field "file-input"
  [form-class m]
  (file-input m))

(defn inline-errs-defs
  "Used by make-form to add errors and defaults for form fields of
  type 'inline-fields.' Adds an :errors and :default to each inline
  field."
  [{:keys [columns] :as m} errors-and-defaults]
  (let [new-columns (map #(merge %
                                 (get errors-and-defaults
                                      (keyword (:name %))))
                         columns)]
    (assoc m :columns new-columns)))

;;  ex: ({:type "text" :name "username" :label "Username"}
;;       {:type "pass" :name "password" :label "Password"})
;; after we merge with errors / defs, one field could look like:
;; {:type "text" :name "username" :errors ["username cannot be blank"] :default ""}
;;Submit-Label is the label on the submit button - default is "Submit"
(defn make-form
  "Returns a form with the specified action, fields, and submit
  button. Fields is a sequence of maps, each containing a form element's
  attributes."
  [& {:keys [action class fields submit-label errors-and-defaults enctype
             cancel-link legend button-type button-attrs method forms-attrs] :as form-map
      :or {class "form-horizontal"
           method "post"}}]
  (basic-form
   (-> (select-keys form-map [:action :legend :method :form-attrs :class :enctype])
       (assoc :fields (map (fn [{:keys [name type] :as a-field}]
                             ;;make-field takes form-class and form
                             ;;field with :errors and :defaults
                             ;;inline-fields have :errors/:defaults
                             ;;inside each form field in :columns
                             (make-field class
                                         (merge a-field
                                                (if (string-contains? type "inline-fields")
                                                  (inline-errs-defs a-field errors-and-defaults)
                                                  (if (not (= type "custom"))
                                                    (get errors-and-defaults
                                                         (keyword
                                                          ;;replace [] in case its
                                                          ;;the name of a form
                                                          ;;element that can take
                                                          ;;on mutliple values (ie checkbox)
                                                          (string/replace name "[]" "")))
                                                    {})))))
                           fields)
              :submitter (if (string-contains? class "form-inline")
                           (button-lite submit-label button-type button-attrs)
                           (when submit-label
                             (make-submit-button submit-label cancel-link button-attrs)))))))


;;MACROS

(defn move-errors-to-flash
  "Moves the errors from sandbar validation and any form-data (values
  that were just submitted) over to the flash to be used when we
  redirect to the form page again. Returns the form data with the sandbar errors."
  [form-data errors]
  (let [form-with-errors (assoc form-data
                           :_sandbar-errors errors)]
    (do
      (session/flash-put! :form-data form-with-errors)
      form-with-errors)))

(defn maybe-conj
  "Gets a list containing one map or many maps. If theres only one map
  in it, return the map. Else, return all the maps in the list conj-ed
  together."
  [a]
  (if (> (count a) 1)
    (apply conj a)
    (first a)))

(defn contains-keys?
  "Like contains? but you pass in a collection of as many keys as you
  want, only returns true if m contains ALL the given keys."
  [m ks]
  (if (next ks)
    (and (contains? m (first ks))
         (contains-keys? m (rest ks)))
    (contains? m (first ks))))

(defn file-input?
  "Checks to see if a given form param is for a file input."
  [m]
  (contains-keys? m [:size :content-type :tempfile :filename]))

;;The fn below is used called by the form-helper macro when a 'form
;;function' is called, ie in a defpage, ex: (myform m "action" "/").
;;It takes in the map of defaults, and checks to see if there is any
;;form-data in the flash (which signifies a failed validation
;;attempt). If there is no flash data, that means its loading the form
;;for the first time, and thus just uses the given defaults. If there
;;is form-data in the flash, then it uses the form params from there
;;and the errors that have been placed in there (by
;;move-errors-to-flash) and generates a map of default
;;and errors suitable for use by make-form.

(defn create-errors-defaults-map
  "Used when a 'form fn' is called, either during the first time a
  form is loaded or on a reload due to a validation error.  It takes
  in a map of default values (which could be empty) and returns a map
  with the form elements as keys, each paired with a map containing
  defaults and errors from validation. Ex: {:somekey {:errors ['error
  mesage'] :default 'default message here'}"
  [default-values] ;;values from a db or something
  (let [flash-data (session/flash-get :form-data) ;;data from prev submission
        flash-errors (:_sandbar-errors flash-data) ;;errors
        m (if (seq flash-data)
            (dissoc flash-data :_sandbar-errors)
            default-values)
        ;;on first load uses default data (ie from db), then POST DATA ONLY on a reload
        defaults (if (seq m)
                   (maybe-conj
                    (map (fn[[k v]] {k {:errors nil :default (if (coll? v)
                                                              (if (file-input? v)
                                                                nil
                                                                (map str v))
                                                              (str v))}}) m))
                   {})
        errors (if (seq flash-errors)
                 (maybe-conj
                  (map (fn[[k v]] {k {:errors v :default ""}}) flash-errors))
                 {})]
    (merge-with (fn [a b]
                  {:errors (:errors b)
                   :default (:default a)})
                defaults errors)))

(defn post-fn
  [& {:keys [validator on-success on-failure]}]
  (fn [req]
    (if-valid validator (:params req)
              on-success
              (comp on-failure move-errors-to-flash))))

(defmacro defform
  "Generates a function that can be used the make the form, and
  generates a *-post function that can be used with Compojure.."
  [sym & {:keys [fields method validator on-success on-failure submit-label]
          :or {on-success (constantly (response/redirect "/"))
               validator  identity
               method "post"}
          :as opts}]
  (assert (and fields on-success on-failure)
          "Please provide :fields, and :on-failure to form-helper.")
  `(do
     (defn ~sym
       ([defaults# action# cancel-link#]
          (->> (create-errors-defaults-map defaults#)
               ;;defaults are values that can be passed in from
               ;;something like a db. If you submitted a form with
               ;;data, and it failed validation, everything has
               ;;already been placed in the flash in
               ;;move-errors-to-flash. Create-errors-defaults-map can
               ;;access values from the flash and make a field that is
               ;;suitable to be passed to make-form
               (assoc (-> (assoc ~opts :action action#)
                          (assoc :cancel-link cancel-link#))
                 :errors-and-defaults)
               (apply concat)
               (apply make-form))))
     (def ~(symbol (str sym "-post"))
       (post-fn :validator ~validator
                :on-success ~on-success
                :on-failure ~on-failure))))
