package com.cupk.aiacademy.rag.visual;

import java.util.List;

/** Coordinates are the model's normalized 0..999 source coordinates; offsets refer to raw OCR text. */
public record VisualRegion(int ordinal,String label,List<Integer> bbox,int sourceStart,int sourceEnd,
                           String caption,String context,String questionNumber) {
    public VisualRegion { bbox=List.copyOf(bbox); }
}
