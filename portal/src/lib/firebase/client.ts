import { getApp, getApps, initializeApp, type FirebaseApp } from "firebase/app";
import { getAuth, type Auth } from "firebase/auth";
import {
  getFirestore,
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  type Firestore,
} from "firebase/firestore";

const config = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY ?? "",
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN ?? "",
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID ?? "",
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET ?? "",
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID ?? "",
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID ?? "",
};

/** O Firebase só é considerado configurado com as variáveis essenciais preenchidas. */
export function firebaseConfigurado(): boolean {
  return Boolean(config.apiKey && config.projectId && config.appId && config.authDomain);
}

function app(): FirebaseApp {
  return getApps().length > 0 ? getApp() : initializeApp(config);
}

export function getFirebaseAuth(): Auth {
  return getAuth(app());
}

let db: Firestore | null = null;

export function getDb(): Firestore {
  if (db) return db;
  const a = app();
  try {
    // Persistência offline (IndexedDB) com suporte a várias abas.
    db = initializeFirestore(a, { localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }) });
  } catch {
    // Já inicializado (hot reload) ou IndexedDB indisponível.
    db = getFirestore(a);
  }
  return db;
}
