# Structural spec — ORDERS

> Decision-free, form-object grain. commitStyle: `COMMIT_FORM`

## Census (183 elements)

- Alert: 3
- AttachedLibrary: 1
- Block: 4
- Canvas: 4
- CompoundText: 14
- Coordinate: 1
- DataSourceColumn: 21
- FormModule: 1
- Graphics: 9
- Item: 34
- LOV: 1
- LOVColumnMapping: 2
- Module: 1
- ObjectGroup: 2
- ObjectGroupChild: 12
- ProgramUnit: 4
- RadioButton: 2
- RecordGroup: 1
- RecordGroupColumn: 2
- Relation: 2
- TextSegment: 14
- Trigger: 45
- VisualAttribute: 1
- Window: 2

## Layout

Windows: WIN_ORDER (Orders and Items), WIN_INVENTORY (Stock Levels)

| Canvas | type | window |
|---|---|---|
| CV_ORDER | Content | WIN_ORDER |
| CV_INVENTORY | Content | WIN_INVENTORY |
| CV_HELP | Stacked | WIN_ORDER |
| TOOLBAR | Horizontal Toolbar | WIN_ORDER |

## Blocks

| Block | role | dbBlock | CRUD i/u/d/q (injected) | multiRecord | locking (injected) |
|---|---|---|---|---|---|
| S_ORD | master | True | 1/1/1/1 (True) | False | Automatic (True) |
| S_ITEM | detail | True | 1/1/1/1 (True) | True | Automatic (True) |
| S_INVENTORY | detail | True | 1/1/1/1 (True) | True | Automatic (True) |
| CONTROL | control | False | 1/1/1/1 (False) | False | Automatic (True) |

## Frames (grouping regions — ADR 0012)

- **RECT59** on CV_ORDER title=— encloses: ORD_ID, CUSTOMER_ID, CUSTOMER_NAME, DATE_ORDERED, DATE_SHIPPED, ORDER_FILLED, SALES_REP_ID, SALES_REP_NAME
- **RECT60** on CV_ORDER title=— encloses: PRODUCT_LOV_BUTTON, DESCRIPTION, ITEM_ID, ITEM_TOTAL, PRICE, PRODUCT_ID, QUANTITY, QUANTITY_SHIPPED, TOTAL, PAYMENT_TYPE
- **RECT61** on CV_INVENTORY title=— encloses: AMOUNT_IN_STOCK, MAX_IN_STOCK, REORDER_POINT, RESTOCK_DATE, WAREHOUSE_ID
- **RECT62** on CV_HELP title=— encloses: ORD_ID

## Layout plan (field order + section — ADR 0011/0012)

> Field order is the form's order (Y-then-X), never invented. `section` is the enclosing frame.

- **S_ORD**
  - ID
  - DATE_ORDERED, CUSTOMER_ID, CUSTOMER_NAME [section: RECT59]
  - SALES_REP_ID, SALES_REP_NAME [section: RECT59]
  - DATE_SHIPPED, ORDER_FILLED [section: RECT59]
  - PAYMENT_TYPE [section: RECT60]
- **S_ITEM**
  - PRODUCT_IMAGE
  - IMAGE_DESCRIPTION
  - ORD_ID [section: RECT59]
  - ITEM_ID, PRODUCT_ID, DESCRIPTION, PRICE, QUANTITY, QUANTITY_SHIPPED, ITEM_TOTAL [section: RECT60]
  - TOTAL [section: RECT60]
- **S_INVENTORY**
  - PRODUCT_ID
  - WAREHOUSE_ID, AMOUNT_IN_STOCK, REORDER_POINT, MAX_IN_STOCK, RESTOCK_DATE [section: RECT61]
- **CONTROL**
  - IMMEDIATE, STOCK_BUTTON, IMAGE_BUTTON, SHOW_HELP_BUTTON, EXIT_BUTTON
  - AUTO_QUERY
  - PUSH_BUTTON57
  - PRODUCT_LOV_BUTTON [section: RECT60]

## Block data-source columns (base-table types)

- **S_ORD**: ID NUMBER(7,0)*, CUSTOMER_ID NUMBER(7,0)*, DATE_ORDERED DATE, DATE_SHIPPED DATE, SALES_REP_ID NUMBER(7,0), TOTAL NUMBER(11,2), PAYMENT_TYPE VARCHAR2, ORDER_FILLED VARCHAR2
- **S_ITEM**: ORD_ID NUMBER(7,0)*, ITEM_ID NUMBER(7,0)*, PRODUCT_ID NUMBER(7,0)*, PRICE NUMBER(11,2), QUANTITY NUMBER(9,0), QUANTITY_SHIPPED NUMBER(9,0)
- **S_INVENTORY**: PRODUCT_ID NUMBER(7,0)*, WAREHOUSE_ID NUMBER(7,0)*, AMOUNT_IN_STOCK NUMBER(9,0), REORDER_POINT NUMBER(9,0), MAX_IN_STOCK NUMBER(9,0), OUT_OF_STOCK_EXPLANATION VARCHAR2, RESTOCK_DATE DATE

## Relations

- **S_ORD_S_ITEM**: S_ORD → S_ITEM | DeleteRecord=Non Isolated Deferred=false AutoQuery=false
- **S_ITEM_S_INVENTORY**: S_ITEM → S_INVENTORY | DeleteRecord=Isolated Deferred=true AutoQuery=true

## Dependencies (out-of-slice surfaces — decide per item)

> Each is `status: undecided`. Per the skill workflow, ask the user whether to spec each as a dependency or ignore it — never drop silently.

| Type | Name | Source | Via | Status |
|---|---|---|---|---|
| menu-module | m_summit | — | FormModule.MenuModule | undecided |
| attached-library | CALENDAR | calendar | AttachedLibrary | undecided |
| called-form | pick | — | CALL_FORM/OPEN_FORM/NEW_FORM/RUN_PRODUCT | undecided |
| object-library | CALENDAR | Form_Builder_II.olb | ObjectGroup (subclassed) | undecided |
| global-variable | GLOBAL.CUSTOMER_ID | — | trigger-body scan (:GLOBAL/:PARAMETER) | undecided |
| global-variable | GLOBAL.HEIGHT_* | — | trigger-body scan (:GLOBAL/:PARAMETER) | undecided |
| global-variable | GLOBAL.HEIGHT_WIN_INVENTORY | — | trigger-body scan (:GLOBAL/:PARAMETER) | undecided |
| global-variable | GLOBAL.HEIGHT_WIN_ORDER | — | trigger-body scan (:GLOBAL/:PARAMETER) | undecided |
| global-variable | GLOBAL.SAVE_ITEM_NAME | — | trigger-body scan (:GLOBAL/:PARAMETER) | undecided |
| global-variable | GLOBAL.WHERE_CLS | — | trigger-body scan (:GLOBAL/:PARAMETER) | undecided |
| global-variable | GLOBAL.WIDTH_* | — | trigger-body scan (:GLOBAL/:PARAMETER) | undecided |
| global-variable | GLOBAL.WIDTH_WIN_INVENTORY | — | trigger-body scan (:GLOBAL/:PARAMETER) | undecided |
| global-variable | GLOBAL.WIDTH_WIN_ORDER | — | trigger-body scan (:GLOBAL/:PARAMETER) | undecided |

### Cross-form contracts (:GLOBAL / :PARAMETER usage)

> Shared state this form reads/writes — its runtime contract with calling/called forms. Decide each like any dependency.

- **GLOBAL.CUSTOMER_ID**: S_ORD.PRE-QUERY (read); S_ORD.WHEN-CREATE-RECORD (read); FORM.PRE-FORM (write)
- **GLOBAL.HEIGHT_***: PROGRAM-UNIT.CHECK_WINDOW_SIZE (read)
- **GLOBAL.HEIGHT_WIN_INVENTORY**: FORM.WHEN-NEW-FORM-INSTANCE (write); FORM.WHEN-FORM-NAVIGATE (write)
- **GLOBAL.HEIGHT_WIN_ORDER**: FORM.WHEN-NEW-FORM-INSTANCE (write); FORM.WHEN-FORM-NAVIGATE (write)
- **GLOBAL.SAVE_ITEM_NAME**: CONTROL.SHOW_HELP_BUTTON.WHEN-BUTTON-PRESSED (write); CONTROL.PUSH_BUTTON57.WHEN-BUTTON-PRESSED (read)
- **GLOBAL.WHERE_CLS**: CONTROL.PRODUCT_LOV_BUTTON.WHEN-BUTTON-PRESSED (read); CONTROL.PRODUCT_LOV_BUTTON.WHEN-BUTTON-PRESSED (write)
- **GLOBAL.WIDTH_***: PROGRAM-UNIT.CHECK_WINDOW_SIZE (read)
- **GLOBAL.WIDTH_WIN_INVENTORY**: FORM.WHEN-NEW-FORM-INSTANCE (write); FORM.WHEN-FORM-NAVIGATE (write)
- **GLOBAL.WIDTH_WIN_ORDER**: FORM.WHEN-NEW-FORM-INSTANCE (write); FORM.WHEN-FORM-NAVIGATE (write)

## Alerts

- **DELETE_ALERT** style=Caution buttons=[Yes, No] message=None
- **QUESTION_ALERT** style=None buttons=[Yes, No] message=None
- **PAYMENT_TYPE_ALERT** style=Caution buttons=[None, ] message=This customer must pay cash!

## LOVs

- **PRODUCTS_LOV** (Products) primaryReturnItem=S_ITEM.PRODUCT_ID
  - query: `Select id, name from s_product order by name`
  - col ID (Number) title=Id returnItem=S_ITEM.PRODUCT_ID
  - col NAME (Character) title=Name returnItem=None

## Triggers (name · bodyShape)

- S_ORD.KEY-COMMIT · other
- S_ORD.ON-POPULATE-DETAILS · other
- S_ORD.ON-CHECK-DELETE-MASTER · mixed
- S_ORD.POST-QUERY · select-into
- S_ORD.WHEN-VALIDATE-RECORD · raise-only
- S_ORD.PRE-QUERY · other
- S_ORD.WHEN-CREATE-RECORD · other
- S_ORD.PRE-INSERT · select-into
- S_ORD.KEY-DUPREC · other
- S_ORD.KEY-EXEQRY · other
- S_ORD.KEY-ENTQRY · other
- S_ORD.KEY-CREREC · other
- S_ORD.KEY-CLRBLK · other
- S_ORD.KEY-UP · other
- S_ORD.KEY-DOWN · other
- S_ORD.KEY-OTHERS · other
- S_ORD.KEY-DELREC · other
- S_ORD.WHEN-NEW-RECORD-INSTANCE · other
- S_ORD.CUSTOMER_ID.WHEN-VALIDATE-ITEM · select-into
- S_ORD.DATE_ORDERED.KEY-LISTVAL · other
- S_ORD.DATE_SHIPPED.KEY-LISTVAL · other
- S_ORD.PAYMENT_TYPE.WHEN-RADIO-CHANGED · select-into
- S_ITEM.POST-QUERY · select-into
- S_ITEM.WHEN-NEW-RECORD-INSTANCE · other
- S_ITEM.PRE-INSERT · select-into
- S_ITEM.KEY-DUPREC · other
- S_ITEM.KEY-DELREC · other
- S_ITEM.ON-POPULATE-DETAILS · other
- S_ITEM.KEY-COMMIT · other
- S_ITEM.PRODUCT_ID.WHEN-VALIDATE-ITEM · select-into
- CONTROL.STOCK_BUTTON.WHEN-BUTTON-PRESSED · other
- CONTROL.IMAGE_BUTTON.WHEN-BUTTON-PRESSED · other
- CONTROL.SHOW_HELP_BUTTON.WHEN-BUTTON-PRESSED · other
- CONTROL.EXIT_BUTTON.WHEN-BUTTON-PRESSED · other
- CONTROL.PRODUCT_LOV_BUTTON.WHEN-BUTTON-PRESSED · other
- CONTROL.IMMEDIATE.WHEN-CHECKBOX-CHANGED · other
- CONTROL.AUTO_QUERY.WHEN-CHECKBOX-CHANGED · other
- CONTROL.PUSH_BUTTON57.WHEN-BUTTON-PRESSED · other
- FORM.ON-CLEAR-DETAILS · other
- FORM.WHEN-NEW-FORM-INSTANCE · other
- FORM.PRE-FORM · other
- FORM.KEY-DUPREC · other
- FORM.KEY-EXIT · other
- FORM.WHEN-WINDOW-CLOSED · other
- FORM.WHEN-FORM-NAVIGATE · other

## Option sets (radio / checkbox values)

- S_ORD.PAYMENT_TYPE (Radio Group) init=CASH: Cash=CASH, Credit=CREDIT
- S_ORD.ORDER_FILLED (Check Box) checked=Y unchecked=N init=N label=Order Filled
- CONTROL.IMMEDIATE (Check Box) checked=Y unchecked=N init=Y label=Immediate
- CONTROL.AUTO_QUERY (Check Box) checked=Y unchecked=N init=Y label=Auto Query

## In-form program units

- CHECK_PACKAGE_FAILURE (Procedure)
- QUERY_MASTER_DETAILS (Procedure)
- CLEAR_ALL_MASTER_DETAILS (Procedure)
- CHECK_WINDOW_SIZE (Procedure)

## Reused / deferred objects (recorded, not detailed)

- AttachedLibraries: CALENDAR
- VisualAttributes: CURRENT_RECORD
- ObjectGroups: CALENDAR(9), STOCK_OBJECTS(3)
- MenuModule: m_summit

## Static text (verbatim labels / headers / help)

- CV_ORDER/TEXT91 [heading (h2)]: "Order Information"
- CV_ORDER/TEXT15 [prose]: "Payment Type"
- CV_INVENTORY/TEXT27 [heading (h2)]: "Stock Information"
- CV_HELP/TEXT99 [prose]: "The Orders Form lets you insert, update,&#10;delete and query orders for a cu..."
- CV_HELP/TEXT126 [heading (h2)]: "Help Information"

## Referenced DB program units (resolve signatures from schema)

- GET_PRODUCT_IMAGE — paramShape: unknown (resolve from schema)
- date_lov.get_date — paramShape: unknown (resolve from schema)
