package com.aresstack.drawio.render;

import java.awt.Color;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Locale;

import org.apache.batik.svggen.DefaultExtensionHandler;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGPaintDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Translate Java2D multi-stop gradients into SVG gradient definitions.
 *
 * <p>Batik's {@code SVGGraphics2D} only converts the legacy two-color
 * {@code GradientPaint}; {@code LinearGradientPaint} and
 * {@code RadialGradientPaint}, which {@code Java2DCanvas} uses for Draw.io
 * gradients, are otherwise dropped and the shape is filled black. Gradients
 * use {@code userSpaceOnUse} coordinates, matching Batik's own conversion:
 * they are interpreted in the referencing element's (transformed) user space,
 * exactly as Java2D interprets the paint.
 */
final class SvgGradients extends DefaultExtensionHandler {

    @Override
    public SVGPaintDescriptor handlePaint(Paint paint, SVGGeneratorContext context) {
        if (paint instanceof java.awt.LinearGradientPaint) {
            java.awt.LinearGradientPaint linear = (java.awt.LinearGradientPaint) paint;
            Element gradient = element(context, "linearGradient");
            Point2D start = linear.getStartPoint();
            Point2D end = linear.getEndPoint();
            gradient.setAttributeNS(null, "x1", context.doubleString(start.getX()));
            gradient.setAttributeNS(null, "y1", context.doubleString(start.getY()));
            gradient.setAttributeNS(null, "x2", context.doubleString(end.getX()));
            gradient.setAttributeNS(null, "y2", context.doubleString(end.getY()));
            return describe(gradient, linear, context);
        }
        if (paint instanceof java.awt.RadialGradientPaint) {
            java.awt.RadialGradientPaint radial = (java.awt.RadialGradientPaint) paint;
            Element gradient = element(context, "radialGradient");
            Point2D center = radial.getCenterPoint();
            Point2D focus = radial.getFocusPoint();
            gradient.setAttributeNS(null, "cx", context.doubleString(center.getX()));
            gradient.setAttributeNS(null, "cy", context.doubleString(center.getY()));
            gradient.setAttributeNS(null, "r", context.doubleString(radial.getRadius()));
            gradient.setAttributeNS(null, "fx", context.doubleString(focus.getX()));
            gradient.setAttributeNS(null, "fy", context.doubleString(focus.getY()));
            return describe(gradient, radial, context);
        }
        return super.handlePaint(paint, context);
    }

    private static Element element(SVGGeneratorContext context, String name) {
        Document document = context.getDOMFactory();
        Element gradient = document.createElementNS(SvgOutput.SVG_NAMESPACE, name);
        gradient.setAttributeNS(null, "id", context.getIDGenerator().generateID("gradient"));
        gradient.setAttributeNS(null, "gradientUnits", "userSpaceOnUse");
        return gradient;
    }

    private static SVGPaintDescriptor describe(Element gradient, MultipleGradientPaint paint,
                                               SVGGeneratorContext context) {
        gradient.setAttributeNS(null, "spreadMethod", spread(paint.getCycleMethod()));
        if (paint.getColorSpace() == MultipleGradientPaint.ColorSpaceType.LINEAR_RGB) {
            gradient.setAttributeNS(null, "color-interpolation", "linearRGB");
        }
        AffineTransform transform = paint.getTransform();
        if (!transform.isIdentity()) {
            gradient.setAttributeNS(null, "gradientTransform", String.format(Locale.ROOT,
                    "matrix(%s %s %s %s %s %s)",
                    context.doubleString(transform.getScaleX()),
                    context.doubleString(transform.getShearY()),
                    context.doubleString(transform.getShearX()),
                    context.doubleString(transform.getScaleY()),
                    context.doubleString(transform.getTranslateX()),
                    context.doubleString(transform.getTranslateY())));
        }
        float[] fractions = paint.getFractions();
        Color[] colors = paint.getColors();
        Document document = context.getDOMFactory();
        for (int index = 0; index < fractions.length; index++) {
            Element stop = document.createElementNS(SvgOutput.SVG_NAMESPACE, "stop");
            Color color = colors[index];
            stop.setAttributeNS(null, "offset", context.doubleString(fractions[index]));
            stop.setAttributeNS(null, "stop-color", String.format(Locale.ROOT, "rgb(%d,%d,%d)",
                    color.getRed(), color.getGreen(), color.getBlue()));
            if (color.getAlpha() != 255) {
                stop.setAttributeNS(null, "stop-opacity",
                        context.doubleString(color.getAlpha() / 255.0));
            }
            gradient.appendChild(stop);
        }
        String reference = "url(#" + gradient.getAttributeNS(null, "id") + ")";
        return new SVGPaintDescriptor(reference, "1", gradient);
    }

    private static String spread(MultipleGradientPaint.CycleMethod method) {
        switch (method) {
            case REFLECT:
                return "reflect";
            case REPEAT:
                return "repeat";
            default:
                return "pad";
        }
    }
}
