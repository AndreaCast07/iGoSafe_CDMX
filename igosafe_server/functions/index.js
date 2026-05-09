const { onRequest } = require("firebase-functions/v2/https");
const { onCall } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const h3 = require("h3-js");

admin.initializeApp();
const db = admin.firestore();

/**
 * Función que recibe una lista de coordenadas y devuelve el riesgo basado en H3 y Firestore.
 */
exports.analyzeRoute = onCall({ region: "us-central1" }, async (request) => {
    const points = request.data.points; // Array de {lat, lng}

    if (!points || !Array.isArray(points)) {
        return { error: "Puntos no validos" };
    }

    // 1. Obtener celdas H3 unicas de la ruta
    const uniqueCells = [...new Set(points.map(p =>
        h3.latLngToCell(p.lat, p.lng, 9)
    ))];

    // 2. Consultar riesgos en Firestore por lotes
    let totalRisk = 0;
    let cameraCount = 0;
    let pathCount = 0;

    // Consultamos por cada celda (En producción podrias agrupar estas consultas)
    const promises = uniqueCells.map(cell => db.collection("risks").doc(cell).get());
    const riskDocs = await Promise.all(promises);

    riskDocs.forEach(doc => {
        if (doc.exists) {
            const data = doc.data();
            totalRisk += (data.riesgo_delito || 0) * 1.2 + (data.riesgo_vial || 0) * 0.5;
        }
    });

    // 3. Consultar infraestructura (Cámaras/Senderos)
    const infraPromises = uniqueCells.map(cell => db.collection("infrastructure").doc(cell).get());
    const infraDocs = await Promise.all(infraPromises);

    infraDocs.forEach(doc => {
        if (doc.exists) {
            const data = doc.data();
            cameraCount += (data.cameras ? data.cameras.length : 0);
            pathCount += (data.paths ? data.paths.length : 0);
        }
    });

    const averageRisk = uniqueCells.length > 0 ? totalRisk / uniqueCells.length : 0;

    // Bonus por infraestructura
    const score = Math.max(0, averageRisk - (cameraCount * 5) - (pathCount * 10));

    return {
        score: score,
        cameraCount: cameraCount,
        pathCount: pathCount,
        riskLevel: score > 60 ? "Inseguro" : score > 30 ? "Precaución" : "Seguro"
    };
});
