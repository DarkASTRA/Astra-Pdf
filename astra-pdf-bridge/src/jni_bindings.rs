
#![allow(non_snake_case)]

use std::collections::HashMap;
use std::panic::catch_unwind;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Arc, OnceLock, RwLock};

use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jfloat, jfloatArray, jint, jlong, jstring};

use crate::core::AstraDocument;

fn registry() -> &'static RwLock<HashMap<i64, Arc<AstraDocument>>> {
    static DOC_REGISTRY: OnceLock<RwLock<HashMap<i64, Arc<AstraDocument>>>> = OnceLock::new();
    DOC_REGISTRY.get_or_init(|| RwLock::new(HashMap::new()))
}

static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);

fn throw_java_exception(env: &mut JNIEnv, message: &str) {
    let _ = env.throw_new("java/lang/RuntimeException", message);
}

fn get_doc(handle: jlong) -> Result<Arc<AstraDocument>, String> {
    if handle <= 0 {
        return Err("Invalid document handle".to_string());
    }
    let map = registry()
        .read()
        .map_err(|_| "Document registry lock poisoned".to_string())?;
    map.get(&handle)
        .cloned()
        .ok_or_else(|| "Document handle not found or already closed".to_string())
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_com_astra_pdf_bridge_AstraNativeBridge_nativeOpenDocument(
    mut env: JNIEnv,
    _class: JClass,
    bytes: JByteArray,
    password: JString,
) -> jlong {
    let result = catch_unwind(std::panic::AssertUnwindSafe(|| {
        let pdf_bytes = match env.convert_byte_array(&bytes) {
            Ok(b) => b,
            Err(e) => return Err(format!("Failed to read byte array: {e:?}")),
        };

        let pass_str: Option<String> = if !password.is_null() {
            env.get_string(&password).ok().map(|s| s.into())
        } else {
            None
        };

        match AstraDocument::open(pdf_bytes, pass_str) {
            Ok(doc) => {
                let handle = NEXT_HANDLE.fetch_add(1, Ordering::SeqCst);
                let mut map = registry()
                    .write()
                    .map_err(|_| "Registry write lock poisoned".to_string())?;
                map.insert(handle, Arc::new(doc));
                Ok(handle)
            }
            Err(e) => Err(e.to_string()),
        }
    }));

    match result {
        Ok(Ok(handle)) => handle,
        Ok(Err(err)) => {
            throw_java_exception(&mut env, &err);
            0
        }
        Err(_) => {
            throw_java_exception(&mut env, "Native panic while opening document");
            0
        }
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_com_astra_pdf_bridge_AstraNativeBridge_nativeCloseDocument(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    let _ = catch_unwind(std::panic::AssertUnwindSafe(|| {
        if handle > 0 {
            if let Ok(mut map) = registry().write() {
                map.remove(&handle);
            }
        }
    }));
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_com_astra_pdf_bridge_AstraNativeBridge_nativeGetPageCount(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    let result = catch_unwind(std::panic::AssertUnwindSafe(|| -> Result<jint, String> {
        let doc = get_doc(handle)?;
        Ok(doc.page_count() as jint)
    }));

    match result {
        Ok(Ok(count)) => count,
        Ok(Err(err)) => {
            throw_java_exception(&mut env, &err);
            0
        }
        Err(_) => {
            throw_java_exception(&mut env, "Native panic while getting page count");
            0
        }
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_com_astra_pdf_bridge_AstraNativeBridge_nativeGetPageDimensions(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    page_index: jint,
) -> jfloatArray {
    let result = catch_unwind(std::panic::AssertUnwindSafe(|| -> Result<[f32; 3], String> {
        let doc = get_doc(handle)?;
        let (w, h, r) = doc.page_dimensions(page_index as usize).map_err(|e| e.to_string())?;
        Ok([w, h, r as f32])
    }));

    match result {
        Ok(Ok(dims)) => {
            match env.new_float_array(3) {
                Ok(arr) => {
                    let _ = env.set_float_array_region(&arr, 0, &dims);
                    arr.into_raw()
                }
                Err(e) => {
                    throw_java_exception(&mut env, &format!("Failed to create float array: {e:?}"));
                    std::ptr::null_mut()
                }
            }
        }
        Ok(Err(err)) => {
            throw_java_exception(&mut env, &err);
            std::ptr::null_mut()
        }
        Err(_) => {
            throw_java_exception(&mut env, "Native panic getting page dimensions");
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_com_astra_pdf_bridge_AstraNativeBridge_nativeGetMetadataJson(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let result = catch_unwind(std::panic::AssertUnwindSafe(|| -> Result<String, String> {
        let doc = get_doc(handle)?;
        let meta = doc.metadata();
        serde_json::to_string(&meta).map_err(|e| e.to_string())
    }));

    match result {
        Ok(Ok(json)) => match env.new_string(&json) {
            Ok(s) => s.into_raw(),
            Err(e) => {
                throw_java_exception(&mut env, &format!("String allocation failed: {e:?}"));
                std::ptr::null_mut()
            }
        },
        Ok(Err(err)) => {
            throw_java_exception(&mut env, &err);
            std::ptr::null_mut()
        }
        Err(_) => {
            throw_java_exception(&mut env, "Native panic retrieving metadata");
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_com_astra_pdf_bridge_AstraNativeBridge_nativeRenderPage(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    page_index: jint,
    scale: jfloat,
) -> jbyteArray {
    let result = catch_unwind(std::panic::AssertUnwindSafe(|| -> Result<(u32, u32, Vec<u8>), String> {
        let doc = get_doc(handle)?;
        doc.render_page(page_index as usize, scale).map_err(|e| e.to_string())
    }));

    match result {
        Ok(Ok((w, h, pixels))) => {
            let total_len = 8 + pixels.len();
            let mut payload = Vec::with_capacity(total_len);
            payload.extend_from_slice(&(w as i32).to_be_bytes());
            payload.extend_from_slice(&(h as i32).to_be_bytes());
            payload.extend_from_slice(&pixels);

            match env.byte_array_from_slice(&payload) {
                Ok(arr) => arr.into_raw(),
                Err(e) => {
                    throw_java_exception(&mut env, &format!("Failed to create render byte array: {e:?}"));
                    std::ptr::null_mut()
                }
            }
        }
        Ok(Err(err)) => {
            throw_java_exception(&mut env, &err);
            std::ptr::null_mut()
        }
        Err(_) => {
            throw_java_exception(&mut env, "Native panic during page rendering");
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn Java_com_astra_pdf_bridge_AstraNativeBridge_nativeSearchTextJson(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    query: JString,
) -> jstring {
    let result = catch_unwind(std::panic::AssertUnwindSafe(|| -> Result<String, String> {
        let doc = get_doc(handle)?;
        let q: String = env.get_string(&query).map_err(|e| format!("{e:?}"))?.into();
        let results = doc.search(&q);
        serde_json::to_string(&results).map_err(|e| e.to_string())
    }));

    match result {
        Ok(Ok(json)) => match env.new_string(&json) {
            Ok(s) => s.into_raw(),
            Err(e) => {
                throw_java_exception(&mut env, &format!("String allocation failed: {e:?}"));
                std::ptr::null_mut()
            }
        },
        Ok(Err(err)) => {
            throw_java_exception(&mut env, &err);
            std::ptr::null_mut()
        }
        Err(_) => {
            throw_java_exception(&mut env, "Native panic during search");
            std::ptr::null_mut()
        }
    }
}
