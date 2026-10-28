package tr.com.envapp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginDTO {

  private String kullaniciAdi;
  private String sifre;
  private String locale = "tr-TR";
  private Long organizasyon;

  public LoginDTO(String kullaniciAdi, String sifre, Long organizasyon) {
    this.kullaniciAdi = kullaniciAdi;
    this.sifre = sifre;
    this.organizasyon = organizasyon;
  }

  public String getKullaniciAdi() {
    return kullaniciAdi;
  }

  public void setKullaniciAdi(String kullaniciAdi) {
    this.kullaniciAdi = kullaniciAdi;
  }

  public String getSifre() {
    return sifre;
  }

  public void setSifre(String sifre) {
    this.sifre = sifre;
  }

  public String getLocale() {
    return "tr-TR";
  }

  public Long getOrganizasyon() {
    return organizasyon;
  }

  public void setOrganizasyon(Long organizasyon) {
    this.organizasyon = organizasyon;
  }
}
