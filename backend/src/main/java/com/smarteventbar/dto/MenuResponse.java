package com.smarteventbar.dto;

import com.smarteventbar.model.entity.MixerItem;
import com.smarteventbar.model.entity.PremadeItem;
import com.smarteventbar.model.entity.SpiritItem;

import java.math.BigDecimal;
import java.util.List;

public class MenuResponse {

    private Long stationId;
    private String stationName;
    private BigDecimal cupPrice;
    private List<SpiritItemDto> spirits;
    private List<MixerItemDto> mixers;
    private List<PremadeItemDto> premades;

    public MenuResponse() {
    }

    // Getters and setters
    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    public BigDecimal getCupPrice() { return cupPrice; }
    public void setCupPrice(BigDecimal cupPrice) { this.cupPrice = cupPrice; }
    public List<SpiritItemDto> getSpirits() { return spirits; }
    public void setSpirits(List<SpiritItemDto> spirits) { this.spirits = spirits; }
    public List<MixerItemDto> getMixers() { return mixers; }
    public void setMixers(List<MixerItemDto> mixers) { this.mixers = mixers; }
    public List<PremadeItemDto> getPremades() { return premades; }
    public void setPremades(List<PremadeItemDto> premades) { this.premades = premades; }

    public record SpiritItemDto(Long id, String name, BigDecimal price, boolean available) {
        public static SpiritItemDto fromEntity(SpiritItem item) {
            return new SpiritItemDto(item.getId(), item.getName(), item.getPrice(), item.isAvailable());
        }
    }

    public record MixerItemDto(Long id, String name, BigDecimal price, boolean available) {
        public static MixerItemDto fromEntity(MixerItem item) {
            return new MixerItemDto(item.getId(), item.getName(), item.getPrice(), item.isAvailable());
        }
    }

    public record PremadeItemDto(Long id, String name, String description, BigDecimal price, boolean available) {
        public static PremadeItemDto fromEntity(PremadeItem item) {
            return new PremadeItemDto(item.getId(), item.getName(), item.getDescription(), item.getPrice(), item.isAvailable());
        }
    }
}
