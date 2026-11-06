
use std::sync::Arc;
use hayro::{render_into, render_size, RenderCache, RenderSettings};
use hayro::vello_cpu::color::palette::css::WHITE;
use hayro::hayro_syntax::Pdf;
use hayro::hayro_interpret::InterpreterSettings;
use pdfcraft_cos::Document as CosDoc;
use pdfcraft_render::{DocInfo, PageRenderer, RenderConfig, RenderRequest, RequestKind};
use serde::Serialize;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum AstraError {
    #[error("Failed to open PDF document: {0}")]
    Open(String),
    #[error("Page {0} not found")]
    PageNotFound(usize),
    #[error("Document operation failed: {0}")]
    Operation(String),
}

pub struct AstraDocument {
    pub data: Arc<Vec<u8>>,
    pub password: Option<String>,
    pub pdf: Pdf,
    pub cos_doc: CosDoc,
    pub info: DocInfo,
}

#[derive(Serialize, Clone, Debug)]
pub struct PageDim {
    pub width: f32,
    pub height: f32,
    pub rotation: i64,
}

#[derive(Serialize, Clone, Debug)]
pub struct DocumentMetadata {
    pub page_count: usize,
    pub title: Option<String>,
    pub author: Option<String>,
    pub subject: Option<String>,
    pub producer: Option<String>,
    pub pdf_version: String,
    pub file_size: usize,
    pub pages: Vec<PageDim>,
}

#[derive(Serialize, Clone, Debug)]
pub struct SearchResultItem {
    pub page_index: usize,
    pub match_count: usize,
    pub snippet: String,
}

pub fn search_unicode_text(plain: &str, query: &str) -> Option<(usize, String)> {
    let trimmed = query.trim();
    if trimmed.is_empty() {
        return None;
    }

    let plain_chars: Vec<char> = plain.chars().collect();
    let q_chars: Vec<char> = trimmed.chars().collect();
    if plain_chars.is_empty() || q_chars.is_empty() {
        return None;
    }

    let q_lower: Vec<char> = q_chars.iter().flat_map(|c| c.to_lowercase()).collect();
    if q_lower.is_empty() {
        return None;
    }

    let mut doc_lower: Vec<char> = Vec::with_capacity(plain_chars.len());
    let mut lower_to_orig: Vec<usize> = Vec::with_capacity(plain_chars.len());

    for (orig_idx, &ch) in plain_chars.iter().enumerate() {
        for lower_ch in ch.to_lowercase() {
            doc_lower.push(lower_ch);
            lower_to_orig.push(orig_idx);
        }
    }

    if doc_lower.len() < q_lower.len() {
        return None;
    }

    let mut match_count = 0;
    let mut first_match_orig_idx: Option<usize> = None;

    for i in 0..=(doc_lower.len() - q_lower.len()) {
        if doc_lower[i..i + q_lower.len()] == q_lower[..] {
            match_count += 1;
            if first_match_orig_idx.is_none() {
                first_match_orig_idx = Some(lower_to_orig[i]);
            }
        }
    }

    if match_count > 0 {
        let first_orig = first_match_orig_idx.unwrap_or(0);
        let snippet_start = first_orig.saturating_sub(30);
        let snippet_end = (first_orig + q_chars.len() + 30).min(plain_chars.len());
        let snippet: String = plain_chars[snippet_start..snippet_end]
            .iter()
            .collect::<String>()
            .replace('\n', " ")
            .trim()
            .to_string();

        Some((match_count, snippet))
    } else {
        None
    }
}

impl AstraDocument {
    pub fn open(bytes: Vec<u8>, password: Option<String>) -> Result<Self, AstraError> {
        let arc_bytes = Arc::new(bytes);
        let pass_ref = password.as_deref();

        let doc_info = pdfcraft_render::inspect(arc_bytes.clone(), pass_ref)
            .map_err(|e| AstraError::Open(e.to_string()))?;

        let pdf = Pdf::new_with_password(arc_bytes.clone(), pass_ref.unwrap_or(""))
            .map_err(|e| AstraError::Open(format!("PDF syntax error: {e:?}")))?;

        let cos_doc = CosDoc::open_with_password(arc_bytes.clone(), pass_ref)
            .map_err(|e| AstraError::Open(format!("COS document parse error: {e:?}")))?;

        Ok(AstraDocument {
            data: arc_bytes,
            password,
            pdf,
            cos_doc,
            info: doc_info,
        })
    }

    pub fn page_count(&self) -> usize {
        self.info.pages.len()
    }

    pub fn page_dimensions(&self, page_index: usize) -> Result<(f32, f32, i64), AstraError> {
        let page = self.info.pages.get(page_index).ok_or(AstraError::PageNotFound(page_index))?;
        Ok((page.width, page.height, page.rotation as i64))
    }

    pub fn metadata(&self) -> DocumentMetadata {
        let pages = self.info.pages.iter().map(|p| PageDim {
            width: p.width,
            height: p.height,
            rotation: p.rotation as i64,
        }).collect();

        DocumentMetadata {
            page_count: self.page_count(),
            title: self.info.title.clone(),
            author: self.info.author.clone(),
            subject: self.info.subject.clone(),
            producer: self.info.producer.clone(),
            pdf_version: self.info.pdf_version.clone(),
            file_size: self.data.len(),
            pages,
        }
    }

    pub fn render_page(&self, page_index: usize, scale: f32) -> Result<(u32, u32, Vec<u8>), AstraError> {
        let pages = self.pdf.pages();
        let page = pages.get(page_index).ok_or(AstraError::PageNotFound(page_index))?;
        let (w, h) = page.render_dimensions();

        let s = scale.clamp(0.05, 10.0);
        let rw = (w * s).round().clamp(1.0, 8192.0) as u16;
        let rh = (h * s).round().clamp(1.0, 8192.0) as u16;

        let rs = RenderSettings {
            x_scale: s,
            y_scale: s,
            width: Some(rw),
            height: Some(rh),
            bg_color: WHITE,
            ..Default::default()
        };

        let (out_w, out_h) = render_size(page, &rs);
        let total_pixels = (out_w as usize) * (out_h as usize);
        if total_pixels == 0 || total_pixels > 32 * 1024 * 1024 {
            return Err(AstraError::Operation("Render size out of bounds".into()));
        }

        let mut pixels = vec![0u8; total_pixels * 4];
        let cache = RenderCache::new();
        let settings = InterpreterSettings::default();

        render_into(page, &cache, &settings, &rs, &mut pixels);

        Ok((out_w as u32, out_h as u32, pixels))
    }

    pub fn search(&self, query: &str) -> Vec<SearchResultItem> {
        let trimmed_query = query.trim();
        if trimmed_query.is_empty() {
            return Vec::new();
        }

        let mut renderer = PageRenderer::new(self.data.clone(), RenderConfig::default());
        let mut results = Vec::new();

        for i in 0..self.page_count() {
            let rendered = renderer.render(RenderRequest {
                page: i,
                kind: RequestKind::Text,
                ..Default::default()
            });

            if let Some(text_arc) = rendered.text {
                let plain = text_arc.plain_text();
                if let Some((match_count, snippet)) = search_unicode_text(&plain, trimmed_query) {
                    results.push(SearchResultItem {
                        page_index: i,
                        match_count,
                        snippet,
                    });
                }
            }
        }
        results
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_open_and_render_sample_fixture() {
        let bytes = std::fs::read("../test-fixtures/sample_multipage.pdf")
            .expect("sample_multipage.pdf must exist");
        let doc = AstraDocument::open(bytes, None).expect("Should open sample fixture");
        assert_eq!(doc.page_count(), 3);

        let (w, h, pixels) = doc.render_page(0, 1.0).expect("Should render page 0");
        assert!(w > 0 && h > 0);
        assert_eq!(pixels.len(), (w * h * 4) as usize);

        let meta = doc.metadata();
        assert_eq!(meta.page_count, 3);
        assert_eq!(meta.pages.len(), 3);

        let results = doc.search("Astra");
        assert!(!results.is_empty());
        assert_eq!(results[0].page_index, 0);
    }

    #[test]
    fn test_unicode_search_safety_hindi_and_special_chars() {
        let hindi_text = "यह एक परीक्षण दस्तावेज़ है। इसमें हिन्दी भाषा की सामग्री शामिल है। नमस्ते दुनिया!";
        let res = search_unicode_text(hindi_text, "हिन्दी");
        assert!(res.is_some());
        let (count, snippet) = res.unwrap();
        assert_eq!(count, 1);
        assert!(snippet.contains("हिन्दी"));

        let german_text = "Dies ist ein großes Haus in der Straße.";
        let res_german = search_unicode_text(german_text, "großes");
        assert!(res_german.is_some());
        let _res_german_case = search_unicode_text(german_text, "STRASSE");
        let res_german_lower = search_unicode_text(german_text, "straße");
        assert!(res_german_lower.is_some());

        let res_missing = search_unicode_text(hindi_text, "अनुपस्थित");
        assert!(res_missing.is_none());

        assert!(search_unicode_text(hindi_text, "   ").is_none());
    }

    #[test]
    fn test_large_document_render_and_search() {
        let bytes = std::fs::read("../test-fixtures/large_sample.pdf")
            .expect("large_sample.pdf must exist");
        let start_open = std::time::Instant::now();
        let doc = AstraDocument::open(bytes, None).expect("Should open 25-page fixture");
        let open_duration = start_open.elapsed();
        println!("25-page PDF opened in {open_duration:?}");
        assert_eq!(doc.page_count(), 25);

        for p in [0, 5, 12, 24] {
            let start_render = std::time::Instant::now();
            let (w, h, pixels) = doc.render_page(p, 1.0).expect("Page render should succeed");
            let render_duration = start_render.elapsed();
            println!("Page {p} ({w}x{h}) rendered in {render_duration:?}");
            assert_eq!(pixels.len(), (w * h * 4) as usize);
        }

        let start_search = std::time::Instant::now();
        let results = doc.search("Page 13");
        let search_duration = start_search.elapsed();
        println!("Search across 25 pages took {search_duration:?}");
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].page_index, 12);
    }

    #[test]
    fn test_corrupt_and_empty_document_handling() {
        let empty = Vec::new();
        assert!(AstraDocument::open(empty, None).is_err());

        let corrupt = b"THIS IS NOT A VALID PDF HEADER".to_vec();
        assert!(AstraDocument::open(corrupt, None).is_err());
    }
}
