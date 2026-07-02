# Disposition ledger — ORDERS

> Generated from `docs/generated/ORDERS.spec.json` (spec census: 183 elements). One row per form object + per decision-prone mapped property. The to-issues gate refuses to cut build tickets while any row is unassigned (ADR-0015).

**151 rows — 145 assigned, 6 unassigned.**

## UNASSIGNED (gate fails)

- `property:S_ORD.DATE_ORDERED.initializeValue` — $$date$$
- `trigger:S_ORD.PRE-QUERY` — other
- `trigger:S_ORD.WHEN-CREATE-RECORD` — other
- `property:S_ITEM.QUANTITY_SHIPPED.editability.updateAllowed` — false (insert-only)
- `trigger:FORM.PRE-FORM` — other
- `dependency:global-variable:GLOBAL.CUSTOMER_ID` — trigger-body scan (:GLOBAL/:PARAMETER)

## Rows

| Row | Spec value | Disposition | As / rule | Ref | Reason |
|---|---|---|---|---|---|
| `block:S_ORD` | master (db) | migrated-as | FormLayout master (block-master-single), quick+advanced search load | #39 | — |
| `item:S_ORD.CUSTOMER_NAME` | — | migrated-as | read-only TextField, lookup-filled | #39 | — |
| `property:S_ORD.CUSTOMER_NAME.editability.updateAllowed` | false | migrated-as | read-only state (ADR-0013) | #39 | — |
| `property:S_ORD.CUSTOMER_NAME.editability.insertAllowed` | false | migrated-as | read-only state (ADR-0013) | #39 | — |
| `item:S_ORD.ID` | Text Item | re-decided | read-only TextField (PK display) | #39 | PK is assigned by the PRE-INSERT sequence and never typed on the web; Forms editability existed for ENTER-QUERY mode, replaced by search |
| `item:S_ORD.CUSTOMER_ID` | Text Item | migrated-as | editable TextField + WHEN-VALIDATE-ITEM lookup | #47 | — |
| `property:S_ORD.CUSTOMER_ID.required` | true | migrated-as | required + lookup rejection | #47 | — |
| `trigger:S_ORD.CUSTOMER_ID.WHEN-VALIDATE-ITEM` | select-into | migrated-as | customer lookup defaulter + rejection | #47 | — |
| `item:S_ORD.DATE_ORDERED` | Text Item | migrated-as | DatePicker | #47 | — |
| `property:S_ORD.DATE_ORDERED.initializeValue` | $$date$$ | **UNASSIGNED** | — | — | — |
| `trigger:S_ORD.DATE_ORDERED.KEY-LISTVAL` | other | migrated-as | DatePicker built-in calendar | #47 | — |
| `item:S_ORD.DATE_SHIPPED` | Text Item | migrated-as | DatePicker + ship-date guard | #47 | — |
| `trigger:S_ORD.DATE_SHIPPED.KEY-LISTVAL` | other | migrated-as | DatePicker built-in calendar | #47 | — |
| `item:S_ORD.SALES_REP_ID` | Text Item | re-decided | read-only TextField, lookup-filled | #47 / PR #57 | spec says editable; made lookup-filled-only so direct entry cannot bypass the customer→rep consistency the lookup enforces |
| `item:S_ORD.SALES_REP_NAME` | — | migrated-as | read-only TextField, lookup-filled | #39 | — |
| `property:S_ORD.SALES_REP_NAME.editability.updateAllowed` | false | migrated-as | read-only state (ADR-0013) | #39 | — |
| `property:S_ORD.SALES_REP_NAME.editability.insertAllowed` | false | migrated-as | read-only state (ADR-0013) | #39 | — |
| `item:S_ORD.PAYMENT_TYPE` | Radio Group | migrated-as | RadioButtonGroup Cash/Credit | #47 | — |
| `property:S_ORD.PAYMENT_TYPE.initializeValue` | CASH | migrated-as | Order.blank() defaults CASH | #49 | — |
| `trigger:S_ORD.PAYMENT_TYPE.WHEN-RADIO-CHANGED` | select-into | migrated-as | credit guard (PaymentEligibility) | #47 | — |
| `item:S_ORD.ORDER_FILLED` | Check Box | migrated-as | Checkbox | #47 | — |
| `property:S_ORD.ORDER_FILLED.initializeValue` | N | migrated-as | Order.blank() defaults false (N) | #49 | — |
| `trigger:S_ORD.KEY-COMMIT` | other | migrated-as | Commit button + Ctrl+S, whole-graph @Transactional save | #48 | — |
| `trigger:S_ORD.ON-POPULATE-DETAILS` | other | obsolete | trigger-on-populate-details | — | Forms relation plumbing; loadGraph joins the detail in one read |
| `trigger:S_ORD.ON-CHECK-DELETE-MASTER` | mixed | migrated-as | delete-cascade-guard (Service veto + disabled action) | #45 #49 | — |
| `trigger:S_ORD.POST-QUERY` | select-into | migrated-as | loadGraph SQL join (customer/rep names) | #39 | — |
| `trigger:S_ORD.WHEN-VALIDATE-RECORD` | raise-only | migrated-as | ship-date guard — Binder validator + Service gate | #47 #48 | — |
| `trigger:S_ORD.PRE-QUERY` | other | **UNASSIGNED** | — | — | — |
| `trigger:S_ORD.WHEN-CREATE-RECORD` | other | **UNASSIGNED** | — | — | — |
| `trigger:S_ORD.PRE-INSERT` | select-into | migrated-as | S_ORD_ID.nextval on insert path | #48 | — |
| `trigger:S_ORD.KEY-DUPREC` | other | migrated-as | Duplicate order (clear-list = id only) | #49 | — |
| `trigger:S_ORD.KEY-EXEQRY` | other | migrated-as | quick search commits the result set | #39 #43 | — |
| `trigger:S_ORD.KEY-ENTQRY` | other | re-decided | curated advanced-search panel | PRD #38 / #43 | generic QBE cannot be preserved on the web; curated filters are the recorded honest loss |
| `trigger:S_ORD.KEY-CREREC` | other | migrated-as | New order action | #49 | — |
| `trigger:S_ORD.KEY-CLRBLK` | other | migrated-as | Clear action resets search + form | #43 | — |
| `trigger:S_ORD.KEY-UP` | other | migrated-as | prev/next pager over the result set | #42 | — |
| `trigger:S_ORD.KEY-DOWN` | other | migrated-as | prev/next pager over the result set | #42 | — |
| `trigger:S_ORD.KEY-OTHERS` | other | obsolete | trigger-key-others | — | suppressed Forms function keys; the web has no default keys to suppress |
| `trigger:S_ORD.KEY-DELREC` | other | migrated-as | Delete order + ConfirmDialog | #49 | — |
| `trigger:S_ORD.WHEN-NEW-RECORD-INSTANCE` | other | deferred | — | #68 | image preloading for the current record |
| `block:S_ITEM` | detail (db) | migrated-as | editable Grid detail, unbuffered editor | #40 #45 | — |
| `item:S_ITEM.ORD_ID` | Text Item | migrated-as | implicit FK — line belongs to the order graph | #45 | — |
| `property:S_ITEM.ORD_ID.required` | true | migrated-as | implicit — line exists only inside its order graph | #45 | — |
| `property:S_ITEM.ORD_ID.copyValueFromItem` | S_ORD.ID | migrated-as | implicit — order graph carries the FK | #45 | — |
| `item:S_ITEM.ITEM_ID` | Text Item | migrated-as | read-only Grid column, PRE-INSERT max+1 on add | #45 | — |
| `item:S_ITEM.PRODUCT_ID` | Text Item | migrated-as | ComboBox LOV (PRODUCTS_LOV), direct entry allowed | #46 | — |
| `property:S_ITEM.PRODUCT_ID.required` | true | migrated-as | LOV rejects unknown/empty product | #46 | — |
| `trigger:S_ITEM.PRODUCT_ID.WHEN-VALIDATE-ITEM` | select-into | migrated-as | product defaulter (description/price) | #46 | — |
| `item:S_ITEM.PRICE` | Text Item | migrated-as | editable Grid column (BigDecimalField) | #45 | — |
| `item:S_ITEM.QUANTITY` | Text Item | migrated-as | editable Grid column (IntegerField) | #45 | — |
| `item:S_ITEM.QUANTITY_SHIPPED` | Text Item | migrated-as | editable Grid column (IntegerField) | #45 | — |
| `property:S_ITEM.QUANTITY_SHIPPED.editability.updateAllowed` | false (insert-only) | **UNASSIGNED** | — | — | — |
| `item:S_ITEM.ITEM_TOTAL` | — | migrated-as | computed Grid column — Domain OrderLine.itemTotal() | #44 | — |
| `property:S_ITEM.ITEM_TOTAL.formatMask` | 999,990.99 | migrated-as | money() two-decimal rendering | #44 | — |
| `property:S_ITEM.ITEM_TOTAL.editability.updateAllowed` | false | migrated-as | computed, read-only display | #44 | — |
| `property:S_ITEM.ITEM_TOTAL.editability.insertAllowed` | false | migrated-as | computed, read-only display | #44 | — |
| `item:S_ITEM.TOTAL` | — | migrated-as | Grid footer — Domain Order.total() | #44 | — |
| `property:S_ITEM.TOTAL.formatMask` | 9,999,990.99 | migrated-as | money() two-decimal rendering | #44 | — |
| `item:S_ITEM.DESCRIPTION` | Display Item | migrated-as | read-only Grid column, defaulted from product | #46 | — |
| `item:S_ITEM.PRODUCT_IMAGE` | Image | deferred | — | #68 | — |
| `property:S_ITEM.PRODUCT_IMAGE.visible` | false | deferred | — | #68 | — |
| `item:S_ITEM.IMAGE_DESCRIPTION` | Display Item | deferred | — | #68 | — |
| `trigger:S_ITEM.POST-QUERY` | select-into | migrated-as | loadGraph SQL join (product description) | #40 | — |
| `trigger:S_ITEM.WHEN-NEW-RECORD-INSTANCE` | other | deferred | — | #68 | — |
| `trigger:S_ITEM.PRE-INSERT` | select-into | migrated-as | NVL(MAX(item_id),0)+1 per order (Domain nextItemId) | #45 | — |
| `trigger:S_ITEM.KEY-DUPREC` | other | migrated-as | Duplicate line (clear-list: item_id/product/desc/price) | #45 | — |
| `trigger:S_ITEM.KEY-DELREC` | other | migrated-as | Delete line + ConfirmDialog | #45 | — |
| `trigger:S_ITEM.ON-POPULATE-DETAILS` | other | obsolete | trigger-on-populate-details | — | Forms relation plumbing; S_INVENTORY coordination deferred with #66 |
| `trigger:S_ITEM.KEY-COMMIT` | other | migrated-as | same whole-graph commit | #48 | — |
| `block:S_INVENTORY` | detail (db) | deferred | — | #66 | — |
| `item:S_INVENTORY.PRODUCT_ID` | Text Item | deferred | — | #66 | — |
| `property:S_INVENTORY.PRODUCT_ID.required` | true | deferred | — | #66 | — |
| `property:S_INVENTORY.PRODUCT_ID.copyValueFromItem` | S_ITEM.PRODUCT_ID | deferred | — | #66 | — |
| `item:S_INVENTORY.WAREHOUSE_ID` | Text Item | deferred | — | #66 | — |
| `property:S_INVENTORY.WAREHOUSE_ID.required` | true | deferred | — | #66 | — |
| `item:S_INVENTORY.AMOUNT_IN_STOCK` | Text Item | deferred | — | #66 | — |
| `item:S_INVENTORY.REORDER_POINT` | Text Item | deferred | — | #66 | — |
| `item:S_INVENTORY.MAX_IN_STOCK` | Text Item | deferred | — | #66 | — |
| `item:S_INVENTORY.RESTOCK_DATE` | Text Item | deferred | — | #66 | — |
| `block:CONTROL` | control (control) | migrated-as | LayoutHost — items hoisted into surrounding layout (block-control) | #41 | — |
| `item:CONTROL.STOCK_BUTTON` | Push Button | deferred | — | #66 | — |
| `trigger:CONTROL.STOCK_BUTTON.WHEN-BUTTON-PRESSED` | other | deferred | — | #66 | — |
| `item:CONTROL.IMAGE_BUTTON` | Push Button | deferred | — | #68 | — |
| `trigger:CONTROL.IMAGE_BUTTON.WHEN-BUTTON-PRESSED` | other | deferred | — | #68 | — |
| `item:CONTROL.SHOW_HELP_BUTTON` | Push Button | deferred | — | #72 | — |
| `trigger:CONTROL.SHOW_HELP_BUTTON.WHEN-BUTTON-PRESSED` | other | deferred | — | #72 | — |
| `item:CONTROL.EXIT_BUTTON` | Push Button | deferred | — | #69 | — |
| `trigger:CONTROL.EXIT_BUTTON.WHEN-BUTTON-PRESSED` | other | deferred | — | #69 | — |
| `item:CONTROL.PRODUCT_LOV_BUTTON` | Push Button | deferred | — | #67 | single-value LOV migrated in #46; the multi-select pick protocol is the deferred part |
| `trigger:CONTROL.PRODUCT_LOV_BUTTON.WHEN-BUTTON-PRESSED` | other | deferred | — | #67 | — |
| `item:CONTROL.IMMEDIATE` | Check Box | deferred | — | #71 | — |
| `property:CONTROL.IMMEDIATE.initializeValue` | Y | deferred | — | #71 | — |
| `trigger:CONTROL.IMMEDIATE.WHEN-CHECKBOX-CHANGED` | other | deferred | — | #71 | — |
| `item:CONTROL.AUTO_QUERY` | Check Box | deferred | — | #71 | — |
| `property:CONTROL.AUTO_QUERY.initializeValue` | Y | deferred | — | #71 | — |
| `property:CONTROL.AUTO_QUERY.editability.enabled` | false | deferred | — | #71 | — |
| `trigger:CONTROL.AUTO_QUERY.WHEN-CHECKBOX-CHANGED` | other | deferred | — | #71 | — |
| `item:CONTROL.PUSH_BUTTON57` | Push Button | deferred | — | #72 | — |
| `trigger:CONTROL.PUSH_BUTTON57.WHEN-BUTTON-PRESSED` | other | deferred | — | #72 | — |
| `trigger:FORM.ON-CLEAR-DETAILS` | other | obsolete | trigger-on-clear-details | — | Forms relation plumbing; reload replaces coordinated clearing |
| `trigger:FORM.WHEN-NEW-FORM-INSTANCE` | other | re-decided | search-first empty state (no auto EXECUTE_QUERY) | PRD #38 | Variant A decision: the web view opens on search, not on the full table auto-queried |
| `trigger:FORM.PRE-FORM` | other | **UNASSIGNED** | — | — | — |
| `trigger:FORM.KEY-DUPREC` | other | migrated-as | Duplicate order action | #49 | — |
| `trigger:FORM.KEY-EXIT` | other | deferred | — | #69 | — |
| `trigger:FORM.WHEN-WINDOW-CLOSED` | other | deferred | — | #69 | — |
| `trigger:FORM.WHEN-FORM-NAVIGATE` | other | obsolete | trigger-when-form-navigate | — | router owns navigation; window-geometry bookkeeping is browser-owned |
| `relation:S_ORD_S_ITEM` | S_ITEM.ORD_ID = S_ORD.ID | migrated-as | loadGraph coordination + Non-Isolated delete guard | #40 #45 | — |
| `relation:S_ITEM_S_INVENTORY` | s_item.product_id = s_inventory.product_id | deferred | — | #66 | — |
| `lov:PRODUCTS_LOV` | Select id, name from s_product order by name | migrated-as | ComboBox 'id — name', returns id | #46 | — |
| `record-group:LOV12` | Select id, name from s_product order by name | migrated-as | ProductRepository reference query | #46 | — |
| `alert:DELETE_ALERT` | Caution | migrated-as | ConfirmDialog (order + line delete) | #45 #49 | — |
| `alert:QUESTION_ALERT` | — | deferred | — | #69 | exit confirm belongs to exit wiring |
| `alert:PAYMENT_TYPE_ALERT` | Caution | migrated-as | Notification on credit refusal | #47 | — |
| `program-unit:CHECK_PACKAGE_FAILURE` | Procedure | obsolete | program-unit-default | — | Forms built-in failure propagation; exceptions replace it |
| `program-unit:QUERY_MASTER_DETAILS` | Procedure | obsolete | program-unit-default | — | Forms relation plumbing; loadGraph replaces coordinated querying |
| `program-unit:CLEAR_ALL_MASTER_DETAILS` | Procedure | obsolete | program-unit-default | — | Forms relation plumbing; reload replaces coordinated clearing |
| `program-unit:CHECK_WINDOW_SIZE` | Procedure | obsolete | program-unit-default | — | window-geometry clamping; the browser owns window size |
| `attached-library:CALENDAR` | calendar | deferred | — | #70 | — |
| `visual-attribute:CURRENT_RECORD` | — | obsolete | semanticVisualAttributes | — | current-record highlight is the Grid's selection styling |
| `object-group:CALENDAR` | — | deferred | — | #70 | — |
| `object-group:STOCK_OBJECTS` | — | deferred | — | #66 | — |
| `window:WIN_ORDER` | Orders and Items | migrated-as | @Route view in AppLayout | #41 | — |
| `window:WIN_INVENTORY` | Stock Levels | deferred | — | #66 | — |
| `canvas:CV_ORDER` | Content | migrated-as | VerticalLayout (canvas-content) | #41 | — |
| `canvas:CV_INVENTORY` | Content | deferred | — | #66 | — |
| `canvas:CV_HELP` | Stacked | deferred | — | #72 | — |
| `canvas:TOOLBAR` | Horizontal Toolbar | migrated-as | HorizontalLayout docked top (canvas-horizontal-toolbar) | #41 | — |
| `db-object:GET_PRODUCT_IMAGE` | unknown (resolve from schema) | deferred | — | #68 | — |
| `db-object:date_lov.get_date` | unknown (resolve from schema) | deferred | — | #70 | — |
| `frame:RECT59` | 8 items | migrated-as | titled grouping section (frame-group) | #41 | — |
| `frame:RECT60` | 10 items | migrated-as | items grouping section (frame-group) | #41 | — |
| `frame:RECT61` | 5 items | deferred | — | #66 | — |
| `frame:RECT62` | 1 items | deferred | — | #72 | — |
| `static-text:TEXT91` | heading: Order Information | migrated-as | H2 'Order Information' (boilerplate-heading) | #41 | — |
| `static-text:TEXT15` | prose: Payment Type | migrated-as | RadioButtonGroup label carries it | #41 | — |
| `static-text:TEXT27` | heading: Stock Information | deferred | — | #66 | — |
| `static-text:TEXT99` | prose: The Orders Form lets you insert, update, | deferred | — | #72 | — |
| `static-text:TEXT126` | heading: Help Information | deferred | — | #72 | — |
| `dependency:menu-module:m_summit` | FormModule.MenuModule | deferred | — | #69 | — |
| `dependency:attached-library:CALENDAR` | AttachedLibrary | deferred | — | #70 | — |
| `dependency:called-form:pick` | CALL_FORM/OPEN_FORM/NEW_FORM/RUN_PRODUCT | deferred | — | #67 | — |
| `dependency:object-library:CALENDAR` | ObjectGroup (subclassed) | deferred | — | #70 | — |
| `dependency:global-variable:GLOBAL.CUSTOMER_ID` | trigger-body scan (:GLOBAL/:PARAMETER) | **UNASSIGNED** | — | — | — |
| `dependency:global-variable:GLOBAL.HEIGHT_*` | trigger-body scan (:GLOBAL/:PARAMETER) | obsolete | program-unit-default | — | window-geometry bookkeeping; browser-owned |
| `dependency:global-variable:GLOBAL.HEIGHT_WIN_INVENTORY` | trigger-body scan (:GLOBAL/:PARAMETER) | obsolete | program-unit-default | — | window-geometry bookkeeping; browser-owned |
| `dependency:global-variable:GLOBAL.HEIGHT_WIN_ORDER` | trigger-body scan (:GLOBAL/:PARAMETER) | obsolete | program-unit-default | — | window-geometry bookkeeping; browser-owned |
| `dependency:global-variable:GLOBAL.SAVE_ITEM_NAME` | trigger-body scan (:GLOBAL/:PARAMETER) | deferred | — | #72 | help-overlay focus restore |
| `dependency:global-variable:GLOBAL.WHERE_CLS` | trigger-body scan (:GLOBAL/:PARAMETER) | deferred | — | #67 | pick exclusion protocol |
| `dependency:global-variable:GLOBAL.WIDTH_*` | trigger-body scan (:GLOBAL/:PARAMETER) | obsolete | program-unit-default | — | window-geometry bookkeeping; browser-owned |
| `dependency:global-variable:GLOBAL.WIDTH_WIN_INVENTORY` | trigger-body scan (:GLOBAL/:PARAMETER) | obsolete | program-unit-default | — | window-geometry bookkeeping; browser-owned |
| `dependency:global-variable:GLOBAL.WIDTH_WIN_ORDER` | trigger-body scan (:GLOBAL/:PARAMETER) | obsolete | program-unit-default | — | window-geometry bookkeeping; browser-owned |
