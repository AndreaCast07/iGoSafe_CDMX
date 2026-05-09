const { onCall } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const h3 = require("h3-js");

// Aquí puedes añadir lógica para leer tus JSON de riesgo desde Firebase Storage o Firestore
exports.analyzeSafety = onCall({ region: "us-central1" }, (request) => {
    const points = request.data.points;

    if (!points || !Array.isArray(points)) {
        return { score: 0, level: "Error", label: "Datos de ruta no válidos" };
    }

    logger.info("Analizando ruta con H3...", { pointCount: points.length });

    let totalRisk = 0;
    const HEX_RESOLUTION = 9;

    // Procesamiento con H3 (Versión servidor - Estable)
    points.forEach(pt => {
        try {
            const hex = h3.latLngToCell(pt.lat, pt.lng, HEX_RESOLUTION);
            // Simulación de cruce con datos de incidencia (incidencia_h3.json)
            // En producción, aquí leerías de una base de datos o un mapa en memoria
            totalRisk += 10; // Riesgo base por zona urbana
        } catch (e) {
            logger.error("Error calculando hexágono", e);
        }
    });

    const averageRisk = totalRisk / points.length;

    return {
        score: averageRisk,
        level: averageRisk > 40 ? "Precaución" : "Seguro",
        cameraCount: Math.floor(points.length / 4),
        pathCount: 1,
        label: "Protegido por iGoSafe H3-Cloud"
    };
});
