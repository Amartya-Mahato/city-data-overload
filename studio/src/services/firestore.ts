
"use client";

import { db } from "@/lib/firebase";
import { collection, doc, getDocs, setDoc, updateDoc, query, where, writeBatch } from "firebase/firestore";
import { allAlerts as mockAlerts, type Alert } from "@/lib/data";

const alertsCollection = collection(db, "alerts");
const eventsCollection = collection(db, "events");
const userReportedEventsCollection = collection(db, "user_reported_events");

// Function to upload initial mock data to Firestore
export async function uploadInitialData() {
  const batch = writeBatch(db);
  mockAlerts.forEach((alert) => {
    const docRef = doc(db, "alerts", alert.id);
    batch.set(docRef, alert);
  });
  await batch.commit();
  console.log("Initial data uploaded to Firestore.");
}

// Helper function to map any event/report to the Alert type
const mapToAlert = (doc: any): Alert => {
    const data = doc.data();
    const { location, ...restData } = data;

    let locationString = 'Unknown Location';
    if (typeof location === 'string') {
        locationString = location;
    } else if (location && typeof location === 'object' && location.name) {
        // If the location object has a 'name' property, use it.
        locationString = location.name;
    }

    return {
        id: doc.id,
        department: restData.department || 'Community',
        type: restData.type || restData.name || 'General',
        location: locationString,
        lat: restData.lat || (location && typeof location === 'object' ? location.latitude : 0),
        lng: restData.lng || (location && typeof location === 'object' ? location.longitude : 0),
        urgency: restData.urgency || restData.severity || 'Medium',
        status: restData.status || 'New',
        description: restData.description || restData.snippet || '',
        isEscalated: restData.isEscalated || false,
        date: restData.date || new Date().toISOString().split('T')[0],
        ...restData,
    };
}


// Function to get all alerts from user_reported_events, events, and the original alerts collection
export async function getAllAlerts(): Promise<Alert[]> {
  const userReportedSnapshot = await getDocs(userReportedEventsCollection);
  const cityEventsSnapshot = await getDocs(eventsCollection);
  const alertsSnapshot = await getDocs(alertsCollection);
  
  const alerts: Alert[] = [];
  userReportedSnapshot.forEach((doc) => {
    alerts.push(mapToAlert(doc));
  });
  cityEventsSnapshot.forEach((doc) => {
    alerts.push(mapToAlert(doc));
  });
  alertsSnapshot.forEach((doc) => {
    alerts.push(mapToAlert(doc));
  });

  return alerts;
}

// Function to get alerts for a specific department from the new sources
export async function getAlerts(department: string): Promise<Alert[]> {
  const allAlerts = await getAllAlerts();
  return allAlerts.filter(alert => alert.department === department);
}


// Function to update an alert's status
export async function updateAlertStatus(alertId: string, status: string): Promise<void> {
  // This function now needs to know which collection the alert belongs to.
  // For this prototype, we'll try updating in both, which is not ideal for production.
  try {
    const alertDocRef = doc(db, "user_reported_events", alertId);
    await updateDoc(alertDocRef, { status });
  } catch (e) {
     // if it fails, it might be in the other collection
     try {
        const eventDocRef = doc(db, "events", alertId);
        await updateDoc(eventDocRef, { status });
     } catch (e2) {
        // if it fails again, it might be in the original alerts collection
        const originalAlertDoc = doc(db, "alerts", alertId);
        await updateDoc(originalAlertDoc, { status });
     }
  }
}

// Function to update an alert's status and escalated state
export async function escalateAlert(alertId: string): Promise<void> {
    const updatePayload = { status: "Escalated", isEscalated: true };
     // This function also needs to know which collection the alert belongs to.
    try {
        const alertDoc = doc(db, "user_reported_events", alertId);
        await updateDoc(alertDoc, updatePayload);
    } catch (error) {
       try {
            const eventDoc = doc(db, "events", alertId);
            await updateDoc(eventDoc, updatePayload);
       } catch (e2) {
            const originalAlertDoc = doc(db, "alerts", alertId);
            await updateDoc(originalAlertDoc, updatePayload);
       }
    }
}

// Function to get all events
export async function getEvents(): Promise<any[]> {
    const querySnapshot = await getDocs(eventsCollection);
    const events: any[] = [];
    querySnapshot.forEach((doc) => {
        events.push({ id: doc.id, ...doc.data() });
    });
    return events;
}

// Function to get all user reported events
export async function getUserReportedEvents(): Promise<any[]> {
    const querySnapshot = await getDocs(userReportedEventsCollection);
    const events: any[] = [];
    querySnapshot.forEach((doc) => {
        events.push({ id: doc.id, ...doc.data() });
    });
    return events;
}
