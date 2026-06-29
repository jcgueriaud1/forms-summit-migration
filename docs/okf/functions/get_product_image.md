---
type: Oracle Function
title: GET_PRODUCT_IMAGE
description: Returns the image filename for a product id, or 'No file' when none.
resource: urn:oracle:summit:function:GET_PRODUCT_IMAGE
tags: [oracle, summit, function]
timestamp: 2026-06-29
---

# GET_PRODUCT_IMAGE

Resolves a product's image **filename** by joining `S_PRODUCT.IMAGE_ID` to
`S_IMAGE.ID`, returning the sentinel `'No file'` when there is no matching row or
the filename is null.

Source: `forms-summit-example/init-summit.sql:56644`.

## Signature

```sql
FUNCTION GET_PRODUCT_IMAGE (product_number IN NUMBER) RETURN VARCHAR2
```

| Param | Mode | Type |
|---|---|---|
| product_number | IN | NUMBER |
| **return** | | VARCHAR2 (filename; `'No file'` sentinel) |

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

## Relationships

- Reads [S_PRODUCT](../tables/s_product.md) (`IMAGE_ID`) joined to
  [S_IMAGE](../tables/s_image.md) (`FILENAME`)

## Related

- [tables/s_product.md](../tables/s_product.md) — `IMAGE_ID` join source
- [tables/s_image.md](../tables/s_image.md) — `FILENAME` source
