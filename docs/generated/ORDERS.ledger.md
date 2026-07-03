# Disposition ledger — ORDERS

> Generated from `docs/generated/ORDERS.spec.json` (spec census: 183 elements). One row per form object + per decision-prone mapped property. The to-issues gate refuses to cut build tickets while any row is unassigned (ADR-0015).

**151 rows — 151 assigned, 0 unassigned.**

## Rows

| Row | Spec value | Disposition | As / rule | Ref | Reason |
|---|---|---|---|---|---|
| `block:S_ORD` | master (db) | migrated-as | FormLayout master (block-master-single), quick+advanced search load | #85 | — |
| `item:S_ORD.CUSTOMER_NAME` | — | migrated-as | read-only TextField, lookup-filled | #85 | — |
| `property:S_ORD.CUSTOMER_NAME.editability.updateAllowed` | false | migrated-as | read-only state (ADR-0013) | #85 | — |
| `property:S_ORD.CUSTOMER_NAME.editability.insertAllowed` | false | migrated-as | read-only state (ADR-0013) | #85 | — |
| `item:S_ORD.ID` | Text Item | re-decided | read-only TextField (PK display) | #85 | PK is assigned by the PRE-INSERT sequence and never typed on the web; Forms editability existed for ENTER-QUERY mode, replaced by search |
| `item:S_ORD.CUSTOMER_ID` | Text Item | migrated-as | editable TextField + WHEN-VALIDATE-ITEM lookup | #93 | — |
| `property:S_ORD.CUSTOMER_ID.required` | true | migrated-as | required + lookup rejection | #93 | — |
| `trigger:S_ORD.CUSTOMER_ID.WHEN-VALIDATE-ITEM` | select-into | migrated-as | customer lookup defaulter + rejection | #93 | — |
| `item:S_ORD.DATE_ORDERED` | Text Item | migrated-as | DatePicker | #93 | — |
| `property:S_ORD.DATE_ORDERED.initializeValue` | $$date$$ | migrated-as | DatePicker; Order.blank() defaults date_ordered to today ($$date$$) | #94 | — |
| `trigger:S_ORD.DATE_ORDERED.KEY-LISTVAL` | other | migrated-as | DatePicker built-in calendar | #93 | — |
| `item:S_ORD.DATE_SHIPPED` | Text Item | migrated-as | DatePicker + ship-date guard | #93 | — |
| `trigger:S_ORD.DATE_SHIPPED.KEY-LISTVAL` | other | migrated-as | DatePicker built-in calendar | #93 | — |
| `item:S_ORD.SALES_REP_ID` | Text Item | re-decided | read-only TextField, lookup-filled | #93 | spec says editable; made lookup-filled-only so direct entry cannot bypass the customer→rep consistency the lookup enforces |
| `item:S_ORD.SALES_REP_NAME` | — | migrated-as | read-only TextField, lookup-filled | #85 | — |
| `property:S_ORD.SALES_REP_NAME.editability.updateAllowed` | false | migrated-as | read-only state (ADR-0013) | #85 | — |
| `property:S_ORD.SALES_REP_NAME.editability.insertAllowed` | false | migrated-as | read-only state (ADR-0013) | #85 | — |
| `item:S_ORD.PAYMENT_TYPE` | Radio Group | migrated-as | RadioButtonGroup Cash/Credit | #93 | — |
| `property:S_ORD.PAYMENT_TYPE.initializeValue` | CASH | migrated-as | Order.blank() defaults CASH | #94 | — |
| `trigger:S_ORD.PAYMENT_TYPE.WHEN-RADIO-CHANGED` | select-into | migrated-as | credit guard (PaymentEligibility) | #93 | — |
| `item:S_ORD.ORDER_FILLED` | Check Box | migrated-as | Checkbox | #93 | — |
| `property:S_ORD.ORDER_FILLED.initializeValue` | N | migrated-as | Order.blank() defaults false (N) | #94 | — |
| `trigger:S_ORD.KEY-COMMIT` | other | migrated-as | Commit button + Ctrl+S, whole-graph @Transactional save | #95 | — |
| `trigger:S_ORD.ON-POPULATE-DETAILS` | other | obsolete | trigger-on-populate-details | — | Forms relation plumbing; loadGraph joins the detail in one read |
| `trigger:S_ORD.ON-CHECK-DELETE-MASTER` | mixed | migrated-as | delete-cascade-guard (Service veto + disabled action) | #94 | — |
| `trigger:S_ORD.POST-QUERY` | select-into | migrated-as | loadGraph SQL join (customer/rep names) | #85 | — |
| `trigger:S_ORD.WHEN-VALIDATE-RECORD` | raise-only | migrated-as | ship-date guard — Binder validator + Service gate | #93 | — |
| `trigger:S_ORD.PRE-QUERY` | other | migrated-as | optional customerId route/query param seeds OrderSearchCriteria customer filter (pre-filters search) | #89 | — |
| `trigger:S_ORD.WHEN-CREATE-RECORD` | other | migrated-as | when customerId param present, Order.blank(customerId) defaults new order's customer_id | #94 | — |
| `trigger:S_ORD.PRE-INSERT` | select-into | migrated-as | S_ORD_ID.nextval on insert path | #95 | — |
| `trigger:S_ORD.KEY-DUPREC` | other | migrated-as | Duplicate order (clear-list = id only) | #94 | — |
| `trigger:S_ORD.KEY-EXEQRY` | other | migrated-as | quick search commits the result set | #85 | — |
| `trigger:S_ORD.KEY-ENTQRY` | other | re-decided | curated advanced-search panel | #89 | generic QBE cannot be preserved on the web; curated filters are the recorded honest loss |
| `trigger:S_ORD.KEY-CREREC` | other | migrated-as | New order action | #94 | — |
| `trigger:S_ORD.KEY-CLRBLK` | other | migrated-as | Clear action resets search + form | #89 | — |
| `trigger:S_ORD.KEY-UP` | other | migrated-as | prev/next pager over the result set | #88 | — |
| `trigger:S_ORD.KEY-DOWN` | other | migrated-as | prev/next pager over the result set | #88 | — |
| `trigger:S_ORD.KEY-OTHERS` | other | obsolete | trigger-key-others | — | suppressed Forms function keys; the web has no default keys to suppress |
| `trigger:S_ORD.KEY-DELREC` | other | migrated-as | Delete order + ConfirmDialog | #94 | — |
| `trigger:S_ORD.WHEN-NEW-RECORD-INSTANCE` | other | deferred | — | #80 | image preloading for the current record |
| `block:S_ITEM` | detail (db) | migrated-as | editable Grid detail, unbuffered editor | #86 | — |
| `item:S_ITEM.ORD_ID` | Text Item | migrated-as | implicit FK — line belongs to the order graph | #91 | — |
| `property:S_ITEM.ORD_ID.required` | true | migrated-as | implicit — line exists only inside its order graph | #91 | — |
| `property:S_ITEM.ORD_ID.copyValueFromItem` | S_ORD.ID | migrated-as | implicit — order graph carries the FK | #91 | — |
| `item:S_ITEM.ITEM_ID` | Text Item | migrated-as | read-only Grid column, PRE-INSERT max+1 on add | #91 | — |
| `item:S_ITEM.PRODUCT_ID` | Text Item | migrated-as | ComboBox LOV (PRODUCTS_LOV), direct entry allowed | #92 | — |
| `property:S_ITEM.PRODUCT_ID.required` | true | migrated-as | LOV rejects unknown/empty product | #92 | — |
| `trigger:S_ITEM.PRODUCT_ID.WHEN-VALIDATE-ITEM` | select-into | migrated-as | product defaulter (description/price) | #92 | — |
| `item:S_ITEM.PRICE` | Text Item | migrated-as | editable Grid column (BigDecimalField) | #91 | — |
| `item:S_ITEM.QUANTITY` | Text Item | migrated-as | editable Grid column (IntegerField) | #91 | — |
| `item:S_ITEM.QUANTITY_SHIPPED` | Text Item | migrated-as | editable Grid column (IntegerField) | #91 | — |
| `property:S_ITEM.QUANTITY_SHIPPED.editability.updateAllowed` | false (insert-only) | migrated-as | insert-only Grid editor state (forms-insert-only-field): editable while line is new/unsaved, read-only once persisted, via OrderLine.isNew() | #91 | — |
| `item:S_ITEM.ITEM_TOTAL` | — | migrated-as | computed Grid column — Domain OrderLine.itemTotal() | #90 | — |
| `property:S_ITEM.ITEM_TOTAL.formatMask` | 999,990.99 | migrated-as | money() two-decimal rendering | #90 | — |
| `property:S_ITEM.ITEM_TOTAL.editability.updateAllowed` | false | migrated-as | computed, read-only display | #90 | — |
| `property:S_ITEM.ITEM_TOTAL.editability.insertAllowed` | false | migrated-as | computed, read-only display | #90 | — |
| `item:S_ITEM.TOTAL` | — | migrated-as | Grid footer — Domain Order.total() | #90 | — |
| `property:S_ITEM.TOTAL.formatMask` | 9,999,990.99 | migrated-as | money() two-decimal rendering | #90 | — |
| `item:S_ITEM.DESCRIPTION` | Display Item | migrated-as | read-only Grid column, defaulted from product | #92 | — |
| `item:S_ITEM.PRODUCT_IMAGE` | Image | deferred | — | #80 | — |
| `property:S_ITEM.PRODUCT_IMAGE.visible` | false | deferred | — | #80 | — |
| `item:S_ITEM.IMAGE_DESCRIPTION` | Display Item | deferred | — | #80 | — |
| `trigger:S_ITEM.POST-QUERY` | select-into | migrated-as | loadGraph SQL join (product description) | #86 | — |
| `trigger:S_ITEM.WHEN-NEW-RECORD-INSTANCE` | other | deferred | — | #80 | — |
| `trigger:S_ITEM.PRE-INSERT` | select-into | migrated-as | NVL(MAX(item_id),0)+1 per order (Domain nextItemId) | #91 | — |
| `trigger:S_ITEM.KEY-DUPREC` | other | migrated-as | Duplicate line (clear-list: item_id/product/desc/price) | #91 | — |
| `trigger:S_ITEM.KEY-DELREC` | other | migrated-as | Delete line + ConfirmDialog | #91 | — |
| `trigger:S_ITEM.ON-POPULATE-DETAILS` | other | obsolete | trigger-on-populate-details | — | Forms relation plumbing; S_INVENTORY coordination deferred with #66 |
| `trigger:S_ITEM.KEY-COMMIT` | other | migrated-as | same whole-graph commit | #95 | — |
| `block:S_INVENTORY` | detail (db) | deferred | — | #78 | — |
| `item:S_INVENTORY.PRODUCT_ID` | Text Item | deferred | — | #78 | — |
| `property:S_INVENTORY.PRODUCT_ID.required` | true | deferred | — | #78 | — |
| `property:S_INVENTORY.PRODUCT_ID.copyValueFromItem` | S_ITEM.PRODUCT_ID | deferred | — | #78 | — |
| `item:S_INVENTORY.WAREHOUSE_ID` | Text Item | deferred | — | #78 | — |
| `property:S_INVENTORY.WAREHOUSE_ID.required` | true | deferred | — | #78 | — |
| `item:S_INVENTORY.AMOUNT_IN_STOCK` | Text Item | deferred | — | #78 | — |
| `item:S_INVENTORY.REORDER_POINT` | Text Item | deferred | — | #78 | — |
| `item:S_INVENTORY.MAX_IN_STOCK` | Text Item | deferred | — | #78 | — |
| `item:S_INVENTORY.RESTOCK_DATE` | Text Item | deferred | — | #78 | — |
| `block:CONTROL` | control (control) | migrated-as | LayoutHost — items hoisted into surrounding layout (block-control) | #87 | — |
| `item:CONTROL.STOCK_BUTTON` | Push Button | deferred | — | #78 | — |
| `trigger:CONTROL.STOCK_BUTTON.WHEN-BUTTON-PRESSED` | other | deferred | — | #78 | — |
| `item:CONTROL.IMAGE_BUTTON` | Push Button | deferred | — | #80 | — |
| `trigger:CONTROL.IMAGE_BUTTON.WHEN-BUTTON-PRESSED` | other | deferred | — | #80 | — |
| `item:CONTROL.SHOW_HELP_BUTTON` | Push Button | deferred | — | #84 | — |
| `trigger:CONTROL.SHOW_HELP_BUTTON.WHEN-BUTTON-PRESSED` | other | deferred | — | #84 | — |
| `item:CONTROL.EXIT_BUTTON` | Push Button | deferred | — | #81 | — |
| `trigger:CONTROL.EXIT_BUTTON.WHEN-BUTTON-PRESSED` | other | deferred | — | #81 | — |
| `item:CONTROL.PRODUCT_LOV_BUTTON` | Push Button | deferred | — | #79 | single-value LOV migrated in #46; the multi-select pick protocol is the deferred part |
| `trigger:CONTROL.PRODUCT_LOV_BUTTON.WHEN-BUTTON-PRESSED` | other | deferred | — | #79 | — |
| `item:CONTROL.IMMEDIATE` | Check Box | deferred | — | #83 | — |
| `property:CONTROL.IMMEDIATE.initializeValue` | Y | deferred | — | #83 | — |
| `trigger:CONTROL.IMMEDIATE.WHEN-CHECKBOX-CHANGED` | other | deferred | — | #83 | — |
| `item:CONTROL.AUTO_QUERY` | Check Box | deferred | — | #83 | — |
| `property:CONTROL.AUTO_QUERY.initializeValue` | Y | deferred | — | #83 | — |
| `property:CONTROL.AUTO_QUERY.editability.enabled` | false | deferred | — | #83 | — |
| `trigger:CONTROL.AUTO_QUERY.WHEN-CHECKBOX-CHANGED` | other | deferred | — | #83 | — |
| `item:CONTROL.PUSH_BUTTON57` | Push Button | deferred | — | #84 | — |
| `trigger:CONTROL.PUSH_BUTTON57.WHEN-BUTTON-PRESSED` | other | deferred | — | #84 | — |
| `trigger:FORM.ON-CLEAR-DETAILS` | other | obsolete | trigger-on-clear-details | — | Forms relation plumbing; reload replaces coordinated clearing |
| `trigger:FORM.WHEN-NEW-FORM-INSTANCE` | other | re-decided | search-first empty state (no auto EXECUTE_QUERY) | #89 | Variant A decision: the web view opens on search, not on the full table auto-queried |
| `trigger:FORM.PRE-FORM` | other | migrated-as | view opens unscoped when no customerId param (PRE-FORM cleared GLOBAL.customer_id) | #89 | — |
| `trigger:FORM.KEY-DUPREC` | other | migrated-as | Duplicate order action | #94 | — |
| `trigger:FORM.KEY-EXIT` | other | deferred | — | #81 | — |
| `trigger:FORM.WHEN-WINDOW-CLOSED` | other | deferred | — | #81 | — |
| `trigger:FORM.WHEN-FORM-NAVIGATE` | other | obsolete | trigger-when-form-navigate | — | router owns navigation; window-geometry bookkeeping is browser-owned |
| `relation:S_ORD_S_ITEM` | S_ITEM.ORD_ID = S_ORD.ID | migrated-as | loadGraph coordination + Non-Isolated delete guard | #86 | — |
| `relation:S_ITEM_S_INVENTORY` | s_item.product_id = s_inventory.product_id | deferred | — | #78 | — |
| `lov:PRODUCTS_LOV` | Select id, name from s_product order by name | migrated-as | ComboBox 'id — name', returns id | #92 | — |
| `record-group:LOV12` | Select id, name from s_product order by name | migrated-as | ProductRepository reference query | #92 | — |
| `alert:DELETE_ALERT` | Caution | migrated-as | ConfirmDialog (order + line delete) | #91 | — |
| `alert:QUESTION_ALERT` | — | deferred | — | #81 | exit confirm belongs to exit wiring |
| `alert:PAYMENT_TYPE_ALERT` | Caution | migrated-as | Notification on credit refusal | #93 | — |
| `program-unit:CHECK_PACKAGE_FAILURE` | Procedure | obsolete | program-unit-default | — | Forms built-in failure propagation; exceptions replace it |
| `program-unit:QUERY_MASTER_DETAILS` | Procedure | obsolete | program-unit-default | — | Forms relation plumbing; loadGraph replaces coordinated querying |
| `program-unit:CLEAR_ALL_MASTER_DETAILS` | Procedure | obsolete | program-unit-default | — | Forms relation plumbing; reload replaces coordinated clearing |
| `program-unit:CHECK_WINDOW_SIZE` | Procedure | obsolete | program-unit-default | — | window-geometry clamping; the browser owns window size |
| `attached-library:CALENDAR` | calendar | deferred | — | #82 | — |
| `visual-attribute:CURRENT_RECORD` | — | obsolete | semanticVisualAttributes | — | current-record highlight is the Grid's selection styling |
| `object-group:CALENDAR` | — | deferred | — | #82 | — |
| `object-group:STOCK_OBJECTS` | — | deferred | — | #78 | — |
| `window:WIN_ORDER` | Orders and Items | migrated-as | @Route view in AppLayout | #87 | — |
| `window:WIN_INVENTORY` | Stock Levels | deferred | — | #78 | — |
| `canvas:CV_ORDER` | Content | migrated-as | VerticalLayout (canvas-content) | #87 | — |
| `canvas:CV_INVENTORY` | Content | deferred | — | #78 | — |
| `canvas:CV_HELP` | Stacked | deferred | — | #84 | — |
| `canvas:TOOLBAR` | Horizontal Toolbar | migrated-as | HorizontalLayout docked top (canvas-horizontal-toolbar) | #87 | — |
| `db-object:GET_PRODUCT_IMAGE` | unknown (resolve from schema) | deferred | — | #80 | — |
| `db-object:date_lov.get_date` | unknown (resolve from schema) | deferred | — | #82 | — |
| `frame:RECT59` | 8 items | migrated-as | titled grouping section (frame-group) | #87 | — |
| `frame:RECT60` | 10 items | migrated-as | items grouping section (frame-group) | #87 | — |
| `frame:RECT61` | 5 items | deferred | — | #78 | — |
| `frame:RECT62` | 1 items | deferred | — | #84 | — |
| `static-text:TEXT91` | heading: Order Information | migrated-as | H2 'Order Information' (boilerplate-heading) | #87 | — |
| `static-text:TEXT15` | prose: Payment Type | migrated-as | RadioButtonGroup label carries it | #87 | — |
| `static-text:TEXT27` | heading: Stock Information | deferred | — | #78 | — |
| `static-text:TEXT99` | prose: The Orders Form lets you insert, update, | deferred | — | #84 | — |
| `static-text:TEXT126` | heading: Help Information | deferred | — | #84 | — |
| `dependency:menu-module:m_summit` | FormModule.MenuModule | deferred | — | #81 | — |
| `dependency:attached-library:CALENDAR` | AttachedLibrary | deferred | — | #82 | — |
| `dependency:called-form:pick` | CALL_FORM/OPEN_FORM/NEW_FORM/RUN_PRODUCT | deferred | — | #79 | — |
| `dependency:object-library:CALENDAR` | ObjectGroup (subclassed) | deferred | — | #82 | — |
| `dependency:global-variable:GLOBAL.CUSTOMER_ID` | trigger-body scan (:GLOBAL/:PARAMETER) | migrated-as | modelled as optional customerId route/query parameter on OrderView — a first-class cross-form contract | #89 | — |
| `dependency:global-variable:GLOBAL.HEIGHT_*` | trigger-body scan (:GLOBAL/:PARAMETER) | obsolete | program-unit-default | — | window-geometry bookkeeping; browser-owned |
| `dependency:global-variable:GLOBAL.HEIGHT_WIN_INVENTORY` | trigger-body scan (:GLOBAL/:PARAMETER) | obsolete | program-unit-default | — | window-geometry bookkeeping; browser-owned |
| `dependency:global-variable:GLOBAL.HEIGHT_WIN_ORDER` | trigger-body scan (:GLOBAL/:PARAMETER) | obsolete | program-unit-default | — | window-geometry bookkeeping; browser-owned |
| `dependency:global-variable:GLOBAL.SAVE_ITEM_NAME` | trigger-body scan (:GLOBAL/:PARAMETER) | deferred | — | #84 | help-overlay focus restore |
| `dependency:global-variable:GLOBAL.WHERE_CLS` | trigger-body scan (:GLOBAL/:PARAMETER) | deferred | — | #79 | pick exclusion protocol |
| `dependency:global-variable:GLOBAL.WIDTH_*` | trigger-body scan (:GLOBAL/:PARAMETER) | obsolete | program-unit-default | — | window-geometry bookkeeping; browser-owned |
| `dependency:global-variable:GLOBAL.WIDTH_WIN_INVENTORY` | trigger-body scan (:GLOBAL/:PARAMETER) | obsolete | program-unit-default | — | window-geometry bookkeeping; browser-owned |
| `dependency:global-variable:GLOBAL.WIDTH_WIN_ORDER` | trigger-body scan (:GLOBAL/:PARAMETER) | obsolete | program-unit-default | — | window-geometry bookkeeping; browser-owned |
