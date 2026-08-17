package com.quicktap.pos.print;

/**
 * Fifteen receipt layouts. The Super Admin panel picks the active template by
 * key; {@link ReceiptBuilder} renders the matching header, item block and
 * footer treatment on the thermal printer.
 */
public final class ReceiptTemplates {

    public static final class Template {
        public final String key;
        public final String name;
        /** '=' heavy, '-' light, '*' star, ' ' none. */
        public final char divider;
        public final boolean doubleTitle;
        public final boolean centeredMeta;
        public final boolean showItemPrice;
        public final boolean boxedTotal;
        public final boolean thankYouBlock;

        Template(String key, String name, char divider, boolean doubleTitle,
                 boolean centeredMeta, boolean showItemPrice, boolean boxedTotal,
                 boolean thankYouBlock) {
            this.key = key;
            this.name = name;
            this.divider = divider;
            this.doubleTitle = doubleTitle;
            this.centeredMeta = centeredMeta;
            this.showItemPrice = showItemPrice;
            this.boxedTotal = boxedTotal;
            this.thankYouBlock = thankYouBlock;
        }
    }

    private static final Template[] ALL = new Template[]{
            new Template("classic", "Classic", '=', true, false, true, false, true),
            new Template("minimal", "Minimal", ' ', false, false, false, false, false),
            new Template("compact", "Compact", '-', false, false, false, false, false),
            new Template("boutique", "Boutique", '*', true, true, true, true, true),
            new Template("corporate", "Corporate", '=', false, false, true, true, false),
            new Template("cafe", "Cafe", '-', true, true, true, false, true),
            new Template("retail", "Retail", '=', true, false, true, true, false),
            new Template("wholesale", "Wholesale", '-', false, false, true, true, false),
            new Template("elegant", "Elegant", '*', true, true, false, true, true),
            new Template("thermal58", "Thermal 58", '-', false, true, false, false, true),
            new Template("thermal80", "Thermal 80", '=', true, false, true, true, true),
            new Template("delivery", "Delivery", '-', true, false, true, false, true),
            new Template("kitchen", "Kitchen ticket", '=', true, true, false, false, false),
            new Template("luxury", "Luxury", '*', true, true, true, true, true),
            new Template("invoice", "Tax invoice", '=', false, false, true, true, false),
    };

    private ReceiptTemplates() { }

    public static Template[] all() { return ALL; }

    public static Template byKey(String key) {
        if (key != null) {
            for (Template t : ALL) if (t.key.equalsIgnoreCase(key)) return t;
        }
        return ALL[0];
    }
}
