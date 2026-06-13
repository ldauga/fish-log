package com.fishlog;

public final class ModColors {

    private ModColors() {}

    // ── Raretés ──────────────────────────────────────────────────────────────
    public static final int RARITY_COMMUN     = 0xFFAAAAAA;
    public static final int RARITY_RARE       = 0xFF4488FF;
    public static final int RARITY_EPIQUE     = 0xFFAA44FF;
    public static final int RARITY_EMISSAIRE  = 0xFF44CCAA;
    public static final int RARITY_LEGENDAIRE = 0xFFFFCC00;
    public static final int RARITY_MYTHIQUE   = 0xFFFF4444;
    public static final int RARITY_COSMIQUE   = 0xFFFFFFFF; // rareté vide ""
    public static final int RARITY_PERDU      = 0xFF888888;
    public static final int RARITY_ARTEFACT   = 0xFFFF8844;
    public static final int RARITY_UNKNOWN    = 0xFF888888;

    // ── Appâts ────────────────────────────────────────────────────────────────
    public static final int BAIT_BLEU         = 0xFF4488FF;  // Ver Luisant, Slime Royal
    public static final int BAIT_GRIS         = 0xFF888888;  // Aimant
    public static final int BAIT_VIOLET       = 0xFFAA44FF;  // Appat Runic, Leurre Mecanique
    public static final int BAIT_ORANGE       = 0xFFFFCC00;  // Companion cube, Ruche de Nimeria
    public static final int BAIT_ROUGE        = 0xFFFF4444;  // Zekappat, Lisappat
    public static final int BAIT_BLEU_FONCE   = 0xFF2244AA;  // Lanterne Abyssale, Rune Atlante
    public static final int BAIT_GRIS_CLAIR   = 0xFFAAAAAA;  // Mouche Commune

    // ── Panel / Structure ─────────────────────────────────────────────────────
    public static final int UI_PANEL_BG       = 0xCC111122;
    public static final int UI_HEADER_BG      = 0xFF1A1A33;
    public static final int UI_HEADER_LINE    = 0xFF445566;
    public static final int UI_FOOTER_BG      = 0xFF000000;
    public static final int UI_FOOTER_LINE    = 0xFF334455;
    public static final int UI_DIVIDER        = 0xFF334455;
    public static final int UI_SEARCH_BG      = 0xFF0D0D1A;

    // ── Onglets ───────────────────────────────────────────────────────────────
    public static final int TAB_ACTIVE        = 0xFF334466;
    public static final int TAB_INACTIVE      = 0xFF222233;
    public static final int TAB_DISABLED      = 0xFF1A1A1A;
    public static final int TAB_TEXT_ACTIVE   = 0xFFFFFF;
    public static final int TAB_TEXT_INACTIVE = 0xAAAAAA;
    public static final int TAB_TEXT_DISABLED = 0xFF555555;

    // ── Toggle Pêche / Appâts ─────────────────────────────────────────────────
    public static final int TOGGLE_FISH_BG    = 0xFF223366;
    public static final int TOGGLE_BAIT_BG    = 0xFF226633;
    public static final int TOGGLE_FISH_TEXT  = 0xFFAAAAFF;
    public static final int TOGGLE_BAIT_TEXT  = 0xFF88FF88;
    public static final int TOGGLE_BORDER     = 0xFF8888AA;

    // ── Lignes / Rows ─────────────────────────────────────────────────────────
    public static final int ROW_EVEN          = 0xFF161622;
    public static final int ROW_ODD           = 0xFF1C1C2A;
    public static final int ROW_ODD_ALT       = 0xFF1E1E2E; // onglet RECORDS

    // ── Graphiques ────────────────────────────────────────────────────────────
    public static final int PLOT_BG           = 0xFF1A1A2A;
    public static final int BAR_BG            = 0xFF1A2A1A;
    public static final int CHART_BAND_BG     = 0xFF111122;

    public static final int CHART_FISH_LINE   = 0xFF00AAFF;
    public static final int CHART_FISH_FILL   = 0x4400AAFF;
    public static final int CHART_BAIT_LINE   = 0xFF44CC44;
    public static final int CHART_BAIT_FILL   = 0x4400CC44;

    public static final int CHART_HOURLY_FISH = 0xFF3388CC;
    public static final int CHART_HOURLY_BAIT = 0xFF44AA55;
    public static final int CHART_BAR_CNT     = 0xFF3388CC; // top — fill quantité
    public static final int CHART_BAR_VAL     = 0xFF44CC88; // top — fill valeur
    public static final int CHART_MEDIAN       = 0xFFFFFFFF; // ligne médiane box plot
    public static final int CHART_GRID        = 0x22FFFFFF;
    public static final int CHART_GRID_DENSE  = 0x33FFFFFF;

    // ── Scrollbar ─────────────────────────────────────────────────────────────
    public static final int SB_TRACK          = 0xFF1A1A2A;
    public static final int SB_THUMB          = 0xFF5577AA;
    public static final int SB_THUMB_DRAG     = 0xFF88AADD;

    // ── Bouton LIN/LOG ────────────────────────────────────────────────────────
    public static final int BTN_NORMAL        = 0xFF333355;
    public static final int BTN_ACTIVE        = 0xFF226622;
    public static final int BTN_BORDER        = 0xFF8888AA;
    public static final int BTN_TEXT_NORMAL   = 0xFFAAAAFF;
    public static final int BTN_TEXT_ACTIVE   = 0xFF88FF88;

    // ── Bouton Reverse (▲/▼) ─────────────────────────────────────────────────
    public static final int BTN_REV_NORMAL    = 0xFF223344;
    public static final int BTN_REV_ACTIVE    = 0xFF334422;

    // ── Texte ─────────────────────────────────────────────────────────────────
    public static final int TEXT_WHITE        = 0xFFFFFF;
    public static final int TEXT_LIGHT        = 0xCCCCCC;
    public static final int TEXT_MEDIUM       = 0xDDDDDD;
    public static final int TEXT_MUTED        = 0xAAAAAA;
    public static final int TEXT_MUTED_ARGB   = 0xFFAAAAAA; // même gris, format ARGB complet
    public static final int TEXT_VERY_MUTED   = 0x99888888;
    public static final int TEXT_HEADER_COL   = 0xFFCC88;   // en-têtes de colonnes
    public static final int TEXT_BRAND        = 0xFF6688AA;
    public static final int TEXT_PRICE        = 0xFF88FF88;
    public static final int TEXT_BAIT_FOOTER  = 0xFFFFAA;
    public static final int TEXT_RECENCY      = 0x99BBBBBB;
}
