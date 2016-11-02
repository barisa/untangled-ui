(ns untangled.components.forms
  (:require-macros
    [untangled.client.cards :refer [untangled-app]]
    [devcards.core :as dc :refer [defcard defcard-doc]])
  (:require [clojure.string :as str]
            [om.dom :as dom]
            [om.next :as om :refer [defui]]
            [untangled.i18n :refer [tr]]
            [untangled.components.ui.forms :as f]
            [untangled.dom :as udom]
            [untangled.client.core :as uc]
            [untangled.client.mutations :as m]
            [com.stuartsierra.component :as component]
            [untangled.client.impl.network :as net]))

(declare add-phone-mutation ValidatedPhoneForm)

;; Sample validator that requires there be at least two words
(defmethod f/form-field-valid? 'name-valid? [_ value args]
  (let [trimmed-value (str/trim value)]
    (str/includes? trimmed-value " ")))

(defn field-with-label
  "A non-library helper function, written by you to help lay out your form."
  ([comp form name label] (field-with-label comp form name label nil))
  ([comp form name label validation-message]
   (dom/div #js {:className (str "form-group" (if (f/invalid? form name) " has-error" ""))}
     (dom/label #js {:className "col-sm-2" :htmlFor name} label)
     (dom/div #js {:className "col-sm-10"} (f/form-field comp form name))
     (when (and validation-message (f/invalid? form name))
       (dom/span #js {:className (str "col-sm-offset-2 col-sm-10" name)} validation-message)))))

(defn checkbox-with-label
  "A helper function to lay out checkboxes."
  ([comp form name label] (field-with-label comp form name label nil))
  ([comp form name label validation-message]
   (dom/div #js {:className "checkbox"}
     (dom/label nil (f/form-field comp form name) label))))

(defui ^:once PhoneForm
  static uc/InitialAppState
  (initial-state [this params] (f/build-form this (or params {})))
  static f/IForm
  (form-elements [this] [(f/id-field :db/id)                ; Mark which thing is the ID of this entity
                         (f/text-input :phone/number :class "form-control")
                         (f/dropdown-input :phone/type [(f/option :home "Home") (f/option :work "Work")])])
  static om/IQuery
  (query [this] [:db/id :phone/type :phone/number :ui/form]) ; Don't forget :ui/form!
  static om/Ident
  (ident [this props] [:phone/by-id (:db/id props)])
  Object
  (render [this]
    (let [form (om/props this)]
      (dom/div #js {:className "form-horizontal"}
        (field-with-label this form :phone/type "Phone type:") ; Use your own helpers to render out the fields
        (field-with-label this form :phone/number "Number:")))))

(def ui-phone-form (om/factory PhoneForm {:keyfn :db/id}))

(defn add-phone-mutation [{:keys [state]} k {:keys [id person]}]
  {:action (fn []
             (let [new-phone (f/build-form ValidatedPhoneForm {:db/id id :phone/type :home :phone/number ""})
                   person-ident [:people/by-id person]
                   phone-ident (om/ident ValidatedPhoneForm new-phone)]
               (swap! state assoc-in phone-ident new-phone)
               (uc/integrate-ident! state phone-ident :append (conj person-ident :person/phone-numbers))))})

(defmethod m/mutate 'sample/add-phone [e k p] (add-phone-mutation e k p))

(defui PhoneRoot
  static om/IQuery
  (query [this] [{:phone (om/get-query PhoneForm)}])
  static uc/InitialAppState
  (initial-state [this params]
    (let [phone-number {:db/id 1 :phone/type :home :phone/number "555-1212"}]
      {:phone (uc/initial-state PhoneForm phone-number)}))
  Object
  (render [this]
    (let [{:keys [phone]} (om/props this)]
      (dom/div nil
        (ui-phone-form phone)))))

(defcard-doc
  "# Forms Quick Tour

   Generic form support is a primary rapid appliation development feature. Fortunately, the overall structure of Om Next
   and Untangled makes it relatively simple to write form support in a general-purpose, composeable manner. In fact,
   just a few simple functions make it possible to support:

   - An extensible set of form fields
   - Extensible validation
   - Declarative forms
   - Separation of form UI from form logic
   - Remote integration with form <-> entity
   - Local integration with entities in the browser database

   In the following examples, the following `requires` define the namespaces used:

   ```
   (ns your-ns
     (:require
       [om.dom :as dom]
       [om.next :as om :refer [defui]]
       [untangled.i18n :refer [tr]]
       [untangled.components.ui.forms :as f]
       [untangled.client.core :as uc]
       [untangled.client.mutations :as m]))
   ```

  **IMPORTANT NOTE**: When we use the parameter `form` or the word 'form' in the descriptions below, we mean the data
  of the entire entity from an Om table that normally represents something in your application (like a person, phone number, etc).
  This library *augments* your database entry with form support data (your 'person' becomes a 'person' AND a 'form'). In
  raw technical terms, the `build-form` function takes a map, and adds a `:ui/form { ... }` entry *to it*.

  ## Form Elements - Declarative Form Definition

  Form fields are most conveniently declared on the ui component that will render the form. The fields themselves
  are declared with function calls that correspond to the field type:

  - `id-field` : A (meant-to-be-hidden) form field that corresponds to the attribute that uniquely identifies the entity being edited. Required for much of the interesting support.
  - `subform-element`: An element that indicates a property on this component's query points to a nested form that should be considered part of a larger form set.
  - `text-input` : An optionally validated input for strings.
  - `dropdown-input` : A menu that allows the user to choose from a set of values.
  - `checkbox-input` : A boolean control
  - your-input-here! : Form support is extensible. Whatever interaction you can imagine can be added to a form.

  Form fields are really just simple maps of attributes that describe the configuration of the input.

  The built-in support for doing form logic expects the fields to be declared on the component that will
  render the form, as shown below:

  ```
  (defui PhoneForm
    static f/IForm
    (fields [this] [(f/id-field :db/id)
                    (f/text-input :phone/number)
                    (f/dropdown-input :phone/type [(f/option :home \"Home\") (f/option :work \"Work\")])])
    ...)
  ```

  ### Rendering a Form Field

  The form fields themselves are rendered by calling `(f/form-field form field-name)`. This method **only** renders
  the simple input itself.

  `(f/form-field my-form :name)` --- outputs ---> `(dom/input #js { ... })`

  This is the minimum we can do to ensure that the logic is correctly connected, while not interfering with your
  ability to render the form however you please.

  You'll commonly write some functions of your own that combine other DOM markup with this, such as the function
  below:
  "
  (dc/mkdn-pprint-source field-with-label)
  "

  where functions like `f/invalid?` are used to make decisions about showing/hiding validation messages.

  **The rendering of the form is pretty much up to you! Thus, your forms can be as pretty (or ugly) as you care to make
  them. No worrying about figuring out how we render them, and then trying to make *that* look good.**

  ## A Complete Form Component

  Now that you know how to declare fields on a component and render the controls, the last remaining bit is
  initializing the application state to support the form.

  ### Setting Up the Form State

  A form can augment any entity in an app database table in your client application. The `f/build-form` function
  can take any such entity and add form support to it. The result is perfectly compatible with the original entity.

  "
  (dc/mkdn-pprint-source PhoneForm)
  (dc/mkdn-pprint-source PhoneRoot))



(defcard phone-form
  "The PhoneRoot above was used to generate this simple interactive form"
  (untangled-app PhoneRoot))

(defui ^:once ValidatedPhoneForm
  static uc/InitialAppState
  (initial-state [this params] (f/build-form this (or params {})))
  static f/IForm
  (form-elements [this] [(f/id-field :db/id)
                         (f/text-input :phone/number :validator 'us-phone? :validate-on-blur? true) ; Addition of validator
                         (f/dropdown-input :phone/type [(f/option :home "Home") (f/option :work "Work")])])
  static om/IQuery
  (query [this] [:db/id :phone/type :phone/number :ui/form])
  static om/Ident
  (ident [this props] [:phone/by-id (:db/id props)])
  Object
  (render [this]
    (let [form (om/props this)]
      (dom/div #js {:className "form-horizontal"}
        (field-with-label this form :phone/type "Phone type:")
        ;; One more parameter to give the validation error message:
        (field-with-label this form :phone/number "Number:" "Please format as (###) ###-####")))))

(def ui-vphone-form (om/factory ValidatedPhoneForm))

(defui ValidatedPhoneRoot
  static om/IQuery
  (query [this] [:ui/form {:phone (om/get-query ValidatedPhoneForm)}])
  static uc/InitialAppState
  (initial-state [this params]
    (let [phone-number {:db/id 1 :phone/type :home :phone/number "555-1212"}]
      {:phone (f/build-form ValidatedPhoneForm phone-number)}))
  Object
  (render [this]
    (let [{:keys [phone]} (om/props this)]
      (dom/div nil
        (ui-vphone-form phone)))))

(defmethod f/form-field-valid? 'us-phone? [sym value args]
  (seq (re-matches #"[(][0-9][0-9][0-9][)] [0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]" value)))

(defcard-doc
  "

  **IMPORTANT**: Take special note of the query. It **must** include `:ui/form`, which will pull in the special data needed
  by the form logic. Also note the call in `InitialAppState` to `f/build-form`. This build function is responsible
  for creating the initial state of any component that wishes to act as a form.

  ## Validation

  The validation system is completely extensible as well. There is a multimethod `(f/form-field-valid? [symbol value args])`
  that dispatches on symbol (symbols are allowed in app state, lambdas are not). Form fields that support validation
  can be configured to run validation on interaction.

  Validation is tri-state. The allowed states are `:valid` (checked and correct), `:invalid` (checked and incorrect),
  and `:unchecked`.

  You can trigger full-form validation (which you should do as part of your interaction with the form) by calling
  `(f/validate-entire-form! component form)`. This function invokes a transaction that will update the validation
  markings on all declared fields (which in turn will re-render your UI).

  If you want to check if a form is valid (with updating the markings in the app state...e.g. you want an inline
  answer), then use `(f/valid? (f/validate-fields form))` to get an immediate answer.

  The definition of a validator for US phone numbers could be:

  ```
  (defmethod f/form-field-valid? 'us-phone? [sym value args]
    (seq (re-matches #\"[(][0-9][0-9][0-9][)] [0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]\" value)))
  ```

  The only change would be to add the validator and a message:
  "
  (dc/mkdn-pprint-source ValidatedPhoneForm)
  )

(defcard validated-phone-number
  "Edit the phone field and then set the phone type. The blur will trigger validation"
  (untangled-app ValidatedPhoneRoot))

(defui ^:once PersonForm
  static uc/InitialAppState
  (initial-state [this params] (f/build-form this (or params {})))
  static f/IForm
  (form-elements [this] [(f/id-field :db/id)
                         (f/subform-element :person/phone-numbers ValidatedPhoneForm :many)
                         (f/text-input :person/name :validator 'name-valid? :validate-on-blur? true)
                         (f/integer-input :person/age :validator 'in-range?
                                          :validator-args {:min 1 :max 110} :validate-on-blur? true)
                         (f/checkbox-input :person/registered-to-vote?)])
  static om/IQuery
  ; NOTE: :ui/form-root so that sub-forms will trigger render here
  (query [this] [:ui/form-root :db/id :person/name :person/age
                 :person/registered-to-vote? {:person/phone-numbers (om/get-query ValidatedPhoneForm)} :ui/form])
  static om/Ident
  (ident [this props] [:people/by-id (:db/id props)])
  Object
  (render [this]
    (let [{:keys [person/phone-numbers] :as props} (om/props this)
          dirty? (f/any-dirty? this)
          valid? (f/all-valid? this)]
      (dom/div #js {:className "form-horizontal"}
        (field-with-label this props :person/name "Full Name:" "Please enter your first and last name.")
        (field-with-label this props :person/age "Age:" "That isn't a real age!")
        (checkbox-with-label this props :person/registered-to-vote? "Registered?")
        (when (f/current-value props :person/registered-to-vote?) (dom/div nil "Good on you!"))
        (dom/div nil
          (mapv ui-vphone-form phone-numbers))

        (dom/div #js {:className "button-group"}
          (dom/button #js {:className "btn btn-primary" :onClick #(om/transact! this
                                                                   `[(sample/add-phone ~{:id     (om/tempid)
                                                                                         :person (:db/id props)})])} "Add Phone")
          (dom/button #js {:className "btn btn-default" :onClick #(f/validate-entire-form! this props)} "Validate")
          (dom/button #js {:className "btn btn-default" :disabled (not dirty?)
                           :onClick   (fn []
                                        ;; TODO: Should be able to use fields, subform, and meta on query to focus query
                                        ;; and run post mutations that re-initialize the form state on entities just loaded
                                        (f/reset-from-entity! this))} "UNDO")
          (dom/button #js {:className "btn btn-default" :disabled (not valid?)
                           :onClick   (fn []
                                        ;; TODO: Do we want to add support to trigger follow-on remote read of entity, perhaps as an option?
                                        (f/commit-to-entity! this :remote true))} "Save to entity!"))))))

(def ui-person-form (om/factory PersonForm))

(defui ^:once Root
  static uc/InitialAppState
  (initial-state [this params]
    {:ui/person-id 1
     :person       (uc/initial-state PersonForm
                     {:db/id                      1
                      :person/name                "Tony Kay"
                      :person/age                 23
                      :person/registered-to-vote? false
                      :person/phone-numbers       [(uc/initial-state ValidatedPhoneForm
                                                     {:db/id        22
                                                      :phone/type   :work
                                                      :phone/number "(123) 412-1212"})
                                                   (uc/initial-state ValidatedPhoneForm
                                                     {:db/id        23
                                                      :phone/type   :home
                                                      :phone/number "(541) 555-1212"})]})})
  static om/IQuery
  (query [this] [:ui/person-id {:person (om/get-query PersonForm)}])
  Object
  (render [this]
    (let [{:keys [ui/react-key ui/person-id person]} (om/props this)]
      (dom/div #js {:key react-key}
        (when person
          (ui-person-form person))))))


(defcard-doc
  "
  ## State Evolution

  A form will initially have the field values set to the entity state (passed to `build-form`). As you interact with
  the form the form fields will change, but **the entity itself does not update in the database table**. This allows you to:

  - Reset the form from the entity (TODO: optionally triggering a (re)read from the server)
  - Commit the form changes from the form to the entity (local and (TODO) optionally remote)

  **This, combined with a little server code, makes the form support full stack!**

  You can trigger the following operations on a form:

  - `(f/commit-to-entity! comp)` : Commit the current edits to the entity (no-op if the form doesn't validate)
  - `(f/commit-to-entity! comp true)` : Commit the current edits to the entity AND the server (is a no-op if the form doesn't validate)
  - `(f/reset-from-entity! comp)` : Undo the changes on the form (back to the pristine state of the original), (triggers validation after the reset)
  - More coming...

  The above function calls have corresponding Untangled mutations so you can compose them into your own transactions:

  - `(untangled.components.form/reset-from-entity! {:form-id ident-of-form })`
  - `(untangled.components.form/commit-to-entity! {:form-id ident-of-form :delta delta :remote remote})`

  The delta of a form's edits can be computed by calling:

  `(modified-fields app-state form)`

  See the source of the `commit-to-entity!` and `reset-from-entity!` functions for examples of calling these mutations.

  ### State evolution within your own transactions

  All of the functions described above trigger underlying Om `transact!`. Feel free to read the source of those functions
  and compose the mutations into your own transactions.

  ## Composition

  Form support augments normalized entities in your app database so they can be easily composed! They are UI components,
  and have nothing special about them other than the `:ui/form` state that is added to the entity (though your call of `build-form`).
  You can convert any entity in your database to a form using the `build-form` function, meaning that you can load
  entities as normal, and as you want to edit them
  in a form, simple mutate them into form-compatible entities with `build-form` (which will not touch the original
  properties of the entity, just add `:ui/form`). Then render them with a UI component that shares your entity Ident,
  but has a render method that renders the form fields with `form-field`.

  If you want support for recursive operations on validation, commit, and reset then you can add subforms to your
  `form-elements` return value using the `subform-element` constructor function.

  Here is the source for an application that renders a Person form, where the person can have any nubmer of phone numbers,
  each represented by a nested phone number entity/form. Note the use of `InitialAppState` in Root to build out sample
  data. Also note that the commit/reset buttons traverse to the nested phone forms (TODO: removing transient
  instances)
  "
  (dc/mkdn-pprint-source ValidatedPhoneForm)
  (dc/mkdn-pprint-source PersonForm)
  (dc/mkdn-pprint-source Root)
  "

  ### Composition and Rendering Refresh

  The one caveat is that when forms are nested, the mutations on the nested fields cannot (due to the design of Om) refresh
  the parent automatically. To work around this, all built-in form mutations will trigger follow-on reads of
  the special property `:ui/form-root`. So, if you add that to your parent form's query, rendering of the top-level
  form elements (e.g. buttons that control submission) will properly update.

  ### Sub-forms

  If you add a `subform-element` to your form it indicates that some child is also a form, and should be considered
  part of the overall form set for things like validation and commits. Adding an instance to the application state
  is then no more complicated that adding the instance to the state, as usual (with the added step of adding the
  form data via `build-form`).

  For example, adding a phone number (which is declared as a sub-form) is done via the add-phone-mutation which looks like this:
  "
  (dc/mkdn-pprint-source add-phone-mutation)
  "
  Notice that there is nothing really special going on here. Just add an additional item to the database (which is
  augmented with `f/build-form`) and integrate it's ident!

  If you look carefully at `PersonForm` you'll see the button to trigger adding a phone number, where we're using
  `(om/tempid)` to generate a temporary ID for the new phone number.

  ### Compositional Dirty-Checking, Validation, and Submission

  There are built-in functions for running validation (transactions), and checking things like the dirty state of the
  form:

  - `(all-valid? component)` : Returns true if validation has been run and all editable, modified fields are marked valid. Editing
  a field will changed the validation state to `:unchecked`.

  The `Save` button does a similar thing: it submits the phone numbers, and then the top. Note that Untangled combines
  mutations that happen in the same thread sequence (e.g. you have not given up the thread for rendering). So, all of
  those commits will be sent to the server as a single transaction (if you include the remote parameter).
  ")

(def mock-net (reify net/UntangledNetwork
                (send [this edn ok err]
                  (js/console.log :SERVER-SIM-GOT edn)
                  (ok {}))
                (start [this app] app)))

(defcard sample-form-1
  "This card shows a very simple form in action."
  (untangled-app Root :networking mock-net)
  {}
  {:inspect-data false})

(defcard-doc
  "## Adding Form Field Types

  Adding a new kind of form field is simple:

  - Create a method that returns a map of input configuration values
  - Add a multimethod that can render your field with appropriate hooks into the logic

  The text input field is implemented like this:

  "
  (dc/mkdn-pprint-source f/text-input)
  "

  The keys in an input's configuration map are:

  - `:input/name` : Required. What you want to call the field. Must match an entity property (e.g. :person/name).
  - `:input/type` : Required. Usually namespaced. This should be a unique key that indicates what kind of input you're making
  - `:input/validator` : Optional. Specifies a symbol (dispatch of the form-field-valid? multimethod).
  - `:input/validator-args` : Optional. If there is a validator, it is called with the validator symbol, the questionable value, and these args.
  - Any you want to define : This is a map. Put whatever else you want in this map to help with rendering (e.g. placeholder text,
   class names, style, etc).

  and its renderer looks like this:

  "
  (dc/mkdn-pprint-source f/render-text-field)
  "
  ```
  (defmethod form-field ::text [component form name] (render-text-field component form name))
  ```

  You can retrieve a field's current form value with `(f/current-value form field-name)`, and you can obtain
  your field's configuration (map of :input/??? values) with `(f/field-config form field-name)`.

  The `form-field` multimethod should, in general, return as little as possible, but you are allowed to do whatever you want.
  You are free to make form field renderers that render much more complex DOM, an SVG, etc.

  The following built-in mutations can (and should) be used in your event handlers:

  - `(untangled.components.form/validate {:form-id [:ident/by-x n] :field :field-name})` - Run validation on the given form/field. Marks the form state for the field to `:invalid` or `:valid`. Fields without validators
  will be marked `:valid`.
  - `(untangled.components.form/validate-form! {:form-id [:ident/by-x n])  - Run recursive validation on the given form. Marks the form state for the fields to `:invalid` or `:valid`.
  - `(untangled.components.form/toggle-field {:form-id [:ident/by-x n] :field :field-name)  - Toggle a boolean field value
  - `(untangled.components.form/update-field {:form-id [:ident/by-x n] :field :field-name :value raw-value})` - Set the raw-value (you can use any type) onto the form's placeholder state (not on the entity)

  ## Other Functions of Interest

  Since the `form` is also your entity, you may of course pull any entity data from the `form` map. (E.g. you can
  for example directly access `(:person/name person-form)`). The form attributes are stored under the `:ui/form` key
  and are intended to be opaque. Do not sneak access into the data structure, since we may choose to change the structure
  in future versions. Instead, use these:

  - `f/current-value` : Get the most recent value of a field from a form
  - `f/valid?` : Test if the form (or a field) is currently marked valid (you must run validation separately)
  - `f/dirty?` : Test if the form is dirty (modified). Does not recurse into subforms. Will always return true if the
  entity's ID is a tempid.
  - `f/any-dirty?` : Test if any form or subform in a form set is dirty (modified). Recurses into subforms.
  - `f/invalid?` : Test if the form (or a field) is currently marked invalid (you must run validation separately)
  - `f/field-names` : Get the field names on a form
  - `f/validate-fields` : returns a new version of the form with the fields marked with validation. Pure function.
  - `f/form-ident` : returns the Om Ident of the form (which is also the ident of the entity)
  - `f/validate-entire-form!` : Transacts a mutation that runs and sets validation markers on the form (which will update the UI)
  ")

