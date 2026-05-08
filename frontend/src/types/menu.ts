export interface SpiritItem {
  id: number;
  name: string;
  price: number;
  available: boolean;
}

export interface MixerItem {
  id: number;
  name: string;
  price: number;
  available: boolean;
}

export interface PremadeItem {
  id: number;
  name: string;
  description: string;
  price: number;
  available: boolean;
}

export interface Menu {
  stationId: number;
  stationName: string;
  cupPrice: number;
  spirits: SpiritItem[];
  mixers: MixerItem[];
  premades: PremadeItem[];
}
