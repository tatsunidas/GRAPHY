package com.vis.core.ui.dialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputHandler;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;

/**
 * 
 * TODO 20231008 
 *
 * @author tatsunidas
 * @version 0.1
 */
public class DicomTagsParser implements DicomInputHandler {

	public static ArrayList<DicomTagModel> tagsArray;
	
	private int depth = -1; // ★追加：ネストの深さを管理する変数

	public DicomTagsParser() {
		tagsArray = new ArrayList<DicomTagModel>();
	}

	public ArrayList<DicomTagModel> read(String path) {
		tagsArray = new ArrayList<>();
		depth = 0; // ★追加：新しいファイルを読み込むたびにリセットする
		DicomInputStream dis = null;
		try {
			dis = new DicomInputStream(new File(path));
			parse(dis);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				dis.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return tagsArray;
	}

	public void parse(DicomInputStream dis) throws IOException {
		dis.setDicomInputHandler(this);// このインスタンス自身をハンドラーにセット
		// このメソッドの中で呼ばれるreadFileMetaInformationから、readValueが実行される。その時のメソッドはこのクラスのものになる
//		dis.readDataset(-1, o -> false);//read all
		dis.readDatasetUntilPixelData();//read pre-pixel data
	}

	@Override
	public void readValue(DicomInputStream dis, Attributes attrs) throws IOException {
		DicomTagModel tag = new DicomTagModel();
		setPosition(dis, tag);
		setTagVRAndLength(dis, tag);
		org.dcm4che3.data.VR vr = dis.vr();
		int vallen = dis.length();
		int tagNum = dis.tag();
		boolean undeflen = vallen == -1;
		if (vr == org.dcm4che3.data.VR.SQ || undeflen) {
			setName(dis, tag);
			tagsArray.add(tag);//SQ dose not have value
//			System.out.println(tag.getTagName());
			try {
				dis.readValue(dis, attrs);//repeat
			}catch(java.io.EOFException eof) {
				return;
			}
			if (undeflen) {
				DicomTagModel tag2 = new DicomTagModel();
				setPosition(dis, tag2);
				setTagVRAndLength(dis, tag2);
				setName(dis, tag2);
				tagsArray.add(tag2);
//				System.out.println(tag2.getTagName());
			}
			return;
		}
		//add value
		byte[] b = dis.readValue();
		StringBuilder value = new StringBuilder();
		if (vr.prompt(b, dis.bigEndian(), attrs.getSpecificCharacterSet(), 777, value)) {
			tag.setTagValue(value.toString());
		}
		//add value multiplicity??
		//TODO? https://groups.google.com/forum/#!searchin/dcm4che/value$20multiplicity%7Csort:date/dcm4che/CP7PRqTlMoM/CHAp0sh7UGsJ
		
		//add tag name
		setName(dis,tag);
		tagsArray.add(tag);
		
//		System.out.println(tag.getTagName());
        if (tagNum == Tag.FileMetaInformationGroupLength)
            dis.setFileMetaInformationGroupLength(b);
        else if (tagNum == Tag.TransferSyntaxUID
                || tagNum == Tag.SpecificCharacterSet
                || TagUtils.isPrivateCreator(tagNum))
            attrs.setBytes(tagNum, vr, b);
	}

	private void setPosition(DicomInputStream dis, DicomTagModel tag) {
		tag.setPosition(String.valueOf(dis.getTagPosition()));
	}

	private void setTagVRAndLength(DicomInputStream dis, DicomTagModel tag) {
		// ★変更：タグ番号の文字列（例: 0010,0010）を取得
		String tagStr = TagUtils.toString(dis.tag());
		
		// ★追加：深さが1以上の場合は、その数だけ ">" を先頭にくっつける
		if (depth > 0) {
			StringBuilder prefix = new StringBuilder();
			for (int i = 0; i < depth; i++) {
				prefix.append(">");
			}
			tagStr = prefix.toString() + tagStr;
		}
		
		// 生成した文字列をモデルにセット
		tag.setTag(tagStr);
		tag.setVR(new StringBuilder().append(dis.vr()).toString());
		tag.setTagLength(String.valueOf(dis.length()));
	}

	private void setName(DicomInputStream dis, DicomTagModel tag) {
		tag.setTagName(ElementDictionary.keywordOf(dis.tag(), null));
	}

	@Override
	public void readValue(DicomInputStream dis, Sequence seq) throws IOException {
//		String privateCreator = seq.getParent().getPrivateCreator(dis.tag());
		depth++;
		DicomTagModel tag = new DicomTagModel();
		setPosition(dis, tag);
		setTagVRAndLength(dis, tag);
		setName(dis, tag);
		tagsArray.add(tag);
		boolean undeflen = dis.length() == -1;
		dis.readValue(dis, seq);
		if (undeflen) {
			DicomTagModel tag2 = new DicomTagModel();
			setPosition(dis, tag2);
			setTagVRAndLength(dis, tag2);
			setName(dis, tag2);
			tagsArray.add(tag2);
		}
		depth--; // ★追加：SequenceのItemから抜けるので、深さを-1して元に戻します
	}

	@Override
	public void readValue(DicomInputStream arg0, Fragments arg1) throws IOException {
	}

	@Override
	public void startDataset(DicomInputStream arg0) throws IOException {
	}
	
	@Override
	public void endDataset(DicomInputStream arg0) throws IOException {
	}
}