package moe.yushi.yggdrasil_mock;

import java.util.List;
import java.util.Map;

public class ServerMeta {

	private List<String> skinDomains;
	private String signaturePublickey;
	private Map<String, Object> meta;

	public List<String> getSkinDomains() {
		return skinDomains;
	}

	public void setSkinDomains(List<String> skinDomains) {
		this.skinDomains = skinDomains;
	}

	public String getSignaturePublickey() {
		return signaturePublickey;
	}

	public void setSignaturePublickey(String signaturePublickey) {
		this.signaturePublickey = signaturePublickey;
	}

	public Map<String, Object> getMeta() {
		return meta;
	}

	public void setMeta(Map<String, Object> meta) {
		this.meta = meta;
	}
}
