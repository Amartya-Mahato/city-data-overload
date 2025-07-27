
export type Alert = {
  id: string;
  department: 'Traffic' | 'BMC' | 'Fire' | 'Police' | 'Community' | 'NDRF' | 'Hospitals' | 'Moderator';
  type: string;
  location: string;
  lat: number;
  lng: number;
  urgency: "Low" | "Medium" | "High" | "Critical";
  status: string;
  description: string;
  isEscalated?: boolean;
  assignedTo?: string;
  crowdSize?: string;
  severity?: "Low" | "Medium" | "High" | "Critical";
  date?: string;
  snippet?: string;
  submitter?: string;
  details?: string;
  url?: string;
};

// The single source of truth for all alerts in the system.
export let allAlerts: Alert[] = [
  { id: "ALERT-001", department: "Traffic", type: "Congestion", location: "Koramangala", lat: 12.9352, lng: 77.6245, urgency: "High", status: "New", description: "Major gridlock reported on 1st Main, Koramangala near the Wipro Park junction.", url: "/traffic/congestion", date: "2024-07-29" },
  { id: "ALERT-002", department: "BMC", type: "Pothole", location: "HSR Layout", lat: 12.9121, lng: 77.6446, urgency: "Medium", status: "Assigned", description: "A large pothole has been reported on 27th Main Road in HSR Layout. A maintenance team has been assigned.", url: "/bmc/potholes", date: "2024-07-29", severity: "Medium"},
  { id: "ALERT-003", department: "Fire", type: "Smoke", location: "Indiranagar", lat: 12.9784, lng: 77.6408, urgency: "Critical", status: "Dispatched", description: "Thick smoke has been reported coming from a commercial building on 100 Feet Road, Indiranagar. Fire unit E-02 has been dispatched.", url: "/fire/incidents", date: "2024-07-29" },
  { id: "ALERT-004", department: "Police", type: "Protest", location: "Koramangala", lat: 12.9345, lng: 77.616, urgency: "High", status: "Investigating", description: "A large, unauthorized gathering is forming near the Forum Mall. Police units are on site to monitor the situation.", url: "/police/protests", date: "2024-07-29" },
  { id: "ALERT-005", department: "Community", type: "Community Safety", location: "Jayanagar", lat: 12.9255, lng: 77.5826, urgency: "Low", status: "Resolved", description: "A lost golden retriever was reported in Jayanagar 4th Block. The pet has since been reunited with its owner.", isEscalated: true, url: "/community/events", date: "2024-07-28" },
  { id: "ALERT-006", department: "NDRF", type: "Flood Warning", location: "Bellandur", lat: 12.9304, lng: 77.6784, urgency: "High", status: "Monitoring", description: "Weather models predict a high probability of localized flooding in low-lying areas of Bellandur due to heavy rainfall. NDRF teams are on standby.", url: "/ndrf/alerts", date: "2024-07-29" },
  { id: "ALERT-007", department: "Hospitals", type: "Mass Casualty Incident", location: "Outer Ring Road", lat: 12.9538, lng: 77.6965, urgency: "Critical", status: "ER Activated", description: "A multiple-vehicle collision has occurred on the Outer Ring Road near Marathahalli. Nearby hospitals have activated their ER protocols to receive patients.", url: "/hospitals/alerts", date: "2024-07-29" },
  { id: "ALERT-008", department: "Police", type: "Theft", location: "HSR Layout", lat: 12.9103, lng: 77.6455, urgency: "High", status: "Investigating", description: "A break-in was reported at a residence in HSR Layout, Sector 2. Local police are investigating the scene.", url: "/police/incidents", date: "2024-07-28"},
  { id: "MOD-ESC-01", department: "Moderator", type: "Sensitive Content", snippet: "Violent imagery from protest...", urgency: "High", status: "Pending", description: "Violent imagery from protest in Koramangala requires review.", location: "Koramangala", lat: 12.9352, lng: 77.6245, url: "/moderator/queue", date: "2024-07-29" },
  { id: "BMC-GAR-01", department: "BMC", type: "Garbage", location: "Koramangala", lat: 12.9279, lng: 77.6271, urgency: "Medium", status: "Open", description: "Overflowing garbage bin near Sony World Signal.", url: "/bmc/garbage", date: "2024-07-29" },
  { id: "BMC-SEW-01", department: "BMC", type: "Sewage", location: "Indiranagar", lat: 12.9719, lng: 77.6412, urgency: "High", status: "Open", description: "Sewage overflow on 12th Main Indiranagar.", url: "/bmc/garbage", date: "2024-07-29" },
  { id: "BMC-POT-02", department: "BMC", type: "Pothole", location: "Koramangala", lat: 12.935, lng: 77.624, urgency: "High", status: "New", description: "Large pothole causing issues for two-wheelers.", url: "/bmc/potholes", date: "2024-07-28", severity: "High" },
];

// Allow mutation of the array for the prototype.
// In a real app, this would be a proper state management solution.
export function updateAlert(id: string, newStatus: string) {
    const alertIndex = allAlerts.findIndex(a => a.id === id);
    if (alertIndex !== -1) {
        allAlerts[alertIndex].status = newStatus;
    }
}
