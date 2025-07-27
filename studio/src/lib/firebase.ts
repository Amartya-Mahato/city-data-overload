
// Import the functions you need from the SDKs you need
import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

// Your web app's Firebase configuration will be populated here automatically
const firebaseConfig = {
  apiKey: "AIzaSyBXVg3HZy5QBlMaYsGXOrydkx9CFUUelbM",
  authDomain: "galvanized-sled-466307-q6.firebaseapp.com",
  projectId: "galvanized-sled-466307-q6",
  storageBucket: "galvanized-sled-466307-q6.firebasestorage.app",
  messagingSenderId: "114515649416",
  appId: "1:114515649416:web:fb0932f4dde912e322d03d",
  measurementId: "G-4VKQWNMTFB"
};
// Initialize Firebase
const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
