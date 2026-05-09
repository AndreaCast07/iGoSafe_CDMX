const admin = require("firebase-admin");
const fs = require("fs");
const h3 = require("h3-js");

// Reemplaza con la ruta a tu archivo de credenciales si corres esto localmente
// O usa el SDK si estas en un entorno autenticado
admin.initializeApp({
  projectId: "igosafe-2b97b"
});

const db = admin.firestore();

async function migrateData() {
    console.log("Iniciando migración...");

    // Ejemplo: Migrar incidencia_h3.json
    try {
        const riskData = JSON.parse(fs.readFileSync("../../app/src/main/assets/incidencia_h3.json", "utf8"));
        const batch = db.batch();
        let count = 0;

        for (const [hexId, risk] of Object.entries(riskData)) {
            const ref = db.collection("risks").doc(hexId);
            batch.set(ref, risk);
            count++;

            if (count % 400 === 0) {
                await batch.commit();
                console.log(`Subidos ${count} registros de riesgo...`);
            }
        }
        await batch.commit();
        console.log("Riesgos migrados correctamente.");
    } catch (e) {
        console.error("Error migrando riesgos:", e.message);
    }
}

migrateData();
