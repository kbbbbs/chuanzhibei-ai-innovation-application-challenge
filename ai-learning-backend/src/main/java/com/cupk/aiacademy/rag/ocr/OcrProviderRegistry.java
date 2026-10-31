package com.cupk.aiacademy.rag.ocr;

import com.cupk.aiacademy.model.*;
import org.springframework.stereotype.Component;

/** One explicit provider, no auto-selection or fallback to a different model or OCR engine. */
@Component
public class OcrProviderRegistry {
    private final RuntimeModelProperties settings;private final OcrModelAdapter adapter;
    public OcrProviderRegistry(RuntimeModelProperties settings,OcrModelAdapter adapter){this.settings=settings;this.adapter=adapter;}
    public String configurationStatus(){String value=RuntimeModelPolicy.status(settings.getOcr());return "MODEL_NOT_CONFIGURED".equals(value)?"OCR_NOT_CONFIGURED":value;}
    public OcrProvider provider(){String status=configurationStatus();if(!"CONFIGURED_UNVERIFIED".equals(status))throw new ModelConfigurationException(status);return adapter;}
}
