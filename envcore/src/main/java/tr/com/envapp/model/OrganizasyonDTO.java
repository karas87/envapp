package tr.com.envapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrganizasyonDTO {

  private Long id;
  private String adi;
  private String kisaAdi;
  private String aciklama;
  private Integer durum;
  private Integer seviye;
  private Integer siraNo;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getAdi() {
    return adi;
  }

  public void setAdi(String adi) {
    this.adi = adi;
  }

  public String getKisaAdi() {
    return kisaAdi;
  }

  public void setKisaAdi(String kisaAdi) {
    this.kisaAdi = kisaAdi;
  }

  public String getAciklama() {
    return aciklama;
  }

  public void setAciklama(String aciklama) {
    this.aciklama = aciklama;
  }

  public Integer getDurum() {
    return durum;
  }

  public void setDurum(Integer durum) {
    this.durum = durum;
  }

  public Integer getSeviye() {
    return seviye;
  }

  public void setSeviye(Integer seviye) {
    this.seviye = seviye;
  }

  public Integer getSiraNo() {
    return siraNo;
  }

  public void setSiraNo(Integer siraNo) {
    this.siraNo = siraNo;
  }
}


