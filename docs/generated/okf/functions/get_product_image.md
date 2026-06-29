---
type: Oracle Function
title: GET_PRODUCT_IMAGE
description: Returns the image filename for a product id, or 'No file'; backs the (deferred) product-image display.
resource: urn:oracle:summit:function:GET_PRODUCT_IMAGE
tags: [oracle, summit, function, image, orders, deferred]
timestamp: 2026-06-26
---

# GET_PRODUCT_IMAGE

Resolves a product's image **filename** from `S_PRODUCT.IMAGE_ID → S_IMAGE.FILENAME`,
returning the sentinel `'No file'` when there is no row or the filename is null.
This resolves the `referencedDbObjects` entry the spec marked
`"paramShape: unknown (resolve from schema)"`.

Source: `forms-summit-example/init-summit.sql:56644`.

## Signature

```sql
FUNCTION GET_PRODUCT_IMAGE (product_number IN NUMBER) RETURN VARCHAR2
```

| Param | Mode | Type |
|---|---|---|
| product_number | IN | NUMBER |
| **return** | | VARCHAR2 (filename, max effective length 20; `'No file'` sentinel) |

## Body (verbatim)

```sql
CREATE OR REPLACE FUNCTION GET_PRODUCT_IMAGE (product_number IN NUMBER)
RETURN VARCHAR2 IS
  v_filename VARCHAR2(20);
BEGIN
  SELECT s_image.filename INTO v_filename
  FROM   s_image, s_product
  WHERE  s_image.id = s_product.image_id
         AND s_product.id = product_number;
  IF v_filename IS NULL THEN
    v_filename := 'No file';
  END IF;
  RETURN v_filename;
EXCEPTION
  WHEN no_data_found THEN RETURN 'No file';
END;
```

## Used by ORDERS

- **`S_ITEM` / `S_ORD` WHEN-NEW-RECORD-INSTANCE** and the **`IMAGE_BUTTON`** toggle —
  only when the default-hidden `S_ITEM.PRODUCT_IMAGE` item is visible:
  `filename := GET_PRODUCT_IMAGE(:S_ITEM.product_id);` then
  `READ_IMAGE_FILE(filename,'tiff','S_ITEM.product_image')` unless filename is
  `'No file'`.

## Migration note (deferred construct)

Per `orders-form-spec.md`, the product image is **out of slice**: the filename is
a service-side lookup, not a column, and the underlying files are filesystem
**TIFFs** (`forms-summit-example/*.tif`) which are not browser-native (need
PNG/JPEG conversion). The function effectively *dissolves* — its `SELECT` can be
folded into the product DataProvider query (`S_PRODUCT` join `S_IMAGE`) if the
image is ever brought into scope. Migration decision belongs in
`migration-rules.yaml` (rule `image-item-file-sourced`), not here.

## Related

- [tables/s_product.md](../tables/s_product.md) — `IMAGE_ID` join source
- `../../orders-form-spec.md` § Server-side DB objects
