package com.aresstack.drawio.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Configure one render request: page, output size, background, resources,
 * unsupported-feature handling and limits. Immutable and thread-safe.
 *
 * <pre>{@code
 * RenderOptions options = RenderOptions.builder()
 *         .page(PageSelector.name("Process"))
 *         .scale(2.0)
 *         .background("#ffffff")
 *         .build();
 * }</pre>
 */
public final class RenderOptions {

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final double MAX_SCALE = 16.0;
    private static final int MAX_PADDING = 1024;
    private static final RenderOptions DEFAULTS = builder().build();

    private final PageSelector page;
    private final double scale;
    private final int padding;
    private final String background;
    private final UnsupportedFeaturePolicy unsupportedFeaturePolicy;
    private final ResourcePolicy resourcePolicy;
    private final RenderLimits limits;

    private RenderOptions(Builder builder) {
        this.page = builder.page;
        this.scale = builder.scale;
        this.padding = builder.padding;
        this.background = builder.background;
        this.unsupportedFeaturePolicy = builder.unsupportedFeaturePolicy;
        this.resourcePolicy = builder.resourcePolicy;
        this.limits = builder.limits;
    }

    public static RenderOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder builder = new Builder()
                .page(page)
                .scale(scale)
                .padding(padding)
                .unsupportedFeaturePolicy(unsupportedFeaturePolicy)
                .resourcePolicy(resourcePolicy)
                .limits(limits);
        return background == null ? builder.transparentBackground() : builder.background(background);
    }

    public PageSelector getPage() {
        return page;
    }

    /** Output scale; {@code 1.0} means one draw.io unit is one pixel. */
    public double getScale() {
        return scale;
    }

    /** Padding in output pixels around the diagram bounds. */
    public int getPadding() {
        return padding;
    }

    /** Background color as {@code #rrggbb}, or {@code null} for transparent. */
    public String getBackground() {
        return background;
    }

    public UnsupportedFeaturePolicy getUnsupportedFeaturePolicy() {
        return unsupportedFeaturePolicy;
    }

    public ResourcePolicy getResourcePolicy() {
        return resourcePolicy;
    }

    public RenderLimits getLimits() {
        return limits;
    }

    public static final class Builder {

        private PageSelector page = PageSelector.first();
        private double scale = 1.0;
        private int padding = 0;
        private String background;
        private UnsupportedFeaturePolicy unsupportedFeaturePolicy = UnsupportedFeaturePolicy.FAIL;
        private ResourcePolicy resourcePolicy = ResourcePolicy.none();
        private RenderLimits limits = RenderLimits.defaults();

        private Builder() {
        }

        public Builder page(PageSelector page) {
            this.page = Objects.requireNonNull(page, "page");
            return this;
        }

        public Builder scale(double scale) {
            if (!(scale > 0) || scale > MAX_SCALE || Double.isNaN(scale)) {
                throw new IllegalArgumentException("scale must be > 0 and <= " + MAX_SCALE);
            }
            this.scale = scale;
            return this;
        }

        public Builder padding(int padding) {
            if (padding < 0 || padding > MAX_PADDING) {
                throw new IllegalArgumentException("padding must be between 0 and " + MAX_PADDING);
            }
            this.padding = padding;
            return this;
        }

        public Builder background(String color) {
            Objects.requireNonNull(color, "color");
            if (!HEX_COLOR.matcher(color).matches()) {
                throw new IllegalArgumentException("background must be a #rrggbb color");
            }
            this.background = color.toLowerCase();
            return this;
        }

        public Builder transparentBackground() {
            this.background = null;
            return this;
        }

        public Builder unsupportedFeaturePolicy(UnsupportedFeaturePolicy policy) {
            this.unsupportedFeaturePolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder resourcePolicy(ResourcePolicy policy) {
            this.resourcePolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder limits(RenderLimits limits) {
            this.limits = Objects.requireNonNull(limits, "limits");
            return this;
        }

        public RenderOptions build() {
            return new RenderOptions(this);
        }
    }
}
