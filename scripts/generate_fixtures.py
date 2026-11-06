"""Generate standard minimal PDF test fixtures."""
import os

def create_simple_pdf(filename, num_pages=3):
    objects = []

    catalog_id = 1
    pages_id = 2
    font_id = 3

    page_ids = []
    content_ids = []

    next_id = 4
    for i in range(num_pages):
        page_ids.append(next_id)
        next_id += 1
        content_ids.append(next_id)
        next_id += 1

    out = bytearray()
    out.extend(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")

    offsets = {}

    def write_obj(obj_id, content):
        offsets[obj_id] = len(out)
        out.extend(f"{obj_id} 0 obj\n".encode("ascii"))
        out.extend(content)
        out.extend(b"\nendobj\n")

    write_obj(catalog_id, f"<< /Type /Catalog /Pages {pages_id} 0 R >>".encode("ascii"))

    kids_str = " ".join(f"{pid} 0 R" for pid in page_ids)
    write_obj(pages_id, f"<< /Type /Pages /Kids [{kids_str}] /Count {num_pages} >>".encode("ascii"))

    write_obj(font_id, b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    for i in range(num_pages):
        pid = page_ids[i]
        cid = content_ids[i]

        page_text = f"Astra PDF Sample Document - Page {i+1} of {num_pages}"
        stream_data = (
            f"BT /F1 24 Tf 72 700 Td ({page_text}) Tj ET "
            f"BT /F1 14 Tf 72 650 Td (Verified with PdfCraft engine.) Tj ET "
            f"0.2 0.3 0.8 rg 72 600 450 2 re f"
        ).encode("ascii")

        content_obj = f"<< /Length {len(stream_data)} >>\nstream\n".encode("ascii") + stream_data + b"\nendstream"
        write_obj(cid, content_obj)

        page_obj = (
            f"<< /Type /Page /Parent {pages_id} 0 R "
            f"/MediaBox [0 0 612 792] "
            f"/Contents {cid} 0 R "
            f"/Resources << /Font << /F1 {font_id} 0 R >> >> >>"
        ).encode("ascii")
        write_obj(pid, page_obj)

    xref_offset = len(out)
    total_objs = next_id
    out.extend(f"xref\n0 {total_objs}\n".encode("ascii"))
    out.extend(b"0000000000 65535 f \n")
    for i in range(1, total_objs):
        off = offsets[i]
        out.extend(f"{off:010d} 00000 n \n".encode("ascii"))

    out.extend(
        f"trailer\n<< /Size {total_objs} /Root {catalog_id} 0 R >>\n"
        f"startxref\n{xref_offset}\n%%EOF\n".encode("ascii")
    )

    with open(filename, "wb") as f:
        f.write(out)
    print(f"Created {filename} with {num_pages} pages ({len(out)} bytes)")

if __name__ == "__main__":
    os.makedirs("test-fixtures", exist_ok=True)
    create_simple_pdf("test-fixtures/sample_multipage.pdf", num_pages=3)
    create_simple_pdf("test-fixtures/single_page.pdf", num_pages=1)
