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
| S_ORD | master | True | 1/1/1/1 (True) | True | Automatic (True) |
| S_ITEM | detail | True | 1/1/1/1 (True) | True | Automatic (True) |
| S_INVENTORY | detail | True | 1/1/1/1 (True) | True | Automatic (True) |
| CONTROL | control | False | 1/1/1/1 (False) | True | Automatic (True) |

## Relations

- **S_ORD_S_ITEM**: S_ORD → S_ITEM | DeleteRecord=Non Isolated Deferred=false AutoQuery=false
- **S_ITEM_S_INVENTORY**: S_ITEM → S_INVENTORY | DeleteRecord=Isolated Deferred=true AutoQuery=true

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

## Referenced DB program units (resolve signatures from schema)

- GET_PRODUCT_IMAGE — paramShape: unknown (resolve from schema)
- date_lov.get_date — paramShape: unknown (resolve from schema)
