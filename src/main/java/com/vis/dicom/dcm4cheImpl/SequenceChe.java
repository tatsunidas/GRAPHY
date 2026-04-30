package com.vis.dicom.dcm4cheImpl;

import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.dcm4che3.data.Attributes;
import com.vis.dicom.DicomObject;

public class SequenceChe extends AbstractList<DicomObject> implements com.vis.dicom.Sequence{
	
	private final org.dcm4che3.data.Sequence sequence;
	
    public SequenceChe(org.dcm4che3.data.Sequence sq) {
    	if (sq == null) {
            throw new IllegalArgumentException("Sequence cannot be null");
        }
        this.sequence = sq;
    }
    
    @Override
    public int size() {
        return sequence.size();
    }

    @Override
    public DicomObject get(int index) {
        Attributes attrs = sequence.get(index);
        // AttributesをDicomObjectCheにラップして返す
        return attrs != null ? new DicomObjectChe(attrs) : null;
    }

    @Override
    public DicomObject set(int index, DicomObject element) {
        if (!(element instanceof Attributes)) {
            throw new IllegalArgumentException("Element must be an instance of Attributes (DicomObjectChe)");
        }
        Attributes oldAttrs = sequence.set(index, (Attributes) element);
        return oldAttrs != null ? new DicomObjectChe(oldAttrs) : null;
    }

    @Override
    public void add(int index, DicomObject element) {
        if (!(element instanceof Attributes)) {
            throw new IllegalArgumentException("Element must be an instance of Attributes (DicomObjectChe)");
        }
        sequence.add(index, (Attributes) element);
    }
    
    @Override
    public boolean addTags(Collection<? extends DicomObject> c) {
        if (c == null || c.isEmpty()) {
            return false;
        }
        
        // 1. DicomObject を Attributes に安全にキャストした新しいリストを作成
        List<Attributes> attrsList = c.stream()
                .filter(obj -> obj instanceof Attributes) // Attributesを継承しているかチェック
                .map(obj -> (Attributes) obj)             // キャスト
                .collect(Collectors.toList());            // リストにまとめる

        // 2. まとめて dcm4che の sequence に追加
        return sequence.addAll(attrsList);
    }

    @Override
    public DicomObject remove(int index) {
        Attributes removed = sequence.remove(index);
        return removed != null ? new DicomObjectChe(removed) : null;
    }
    
    // 元のdcm4che3 Sequenceを取り出せるようにしておく（内部処理用）
    public org.dcm4che3.data.Sequence getSequenceChe() {
        return this.sequence;
    }
}
