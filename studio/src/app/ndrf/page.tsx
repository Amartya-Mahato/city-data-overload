
"use client";

import { useState, useEffect } from "react";
import { AlertFeed } from "@/components/dashboards/alert-feed";
import { Waves, Mountain, Truck } from "lucide-react";
import { getAlerts } from "@/services/firestore";
import type { Alert } from "@/lib/data";
import dynamic from 'next/dynamic';
import { Skeleton } from '@/components/ui/skeleton';

const AnalyticsCard = dynamic(() => import('@/components/dashboards/analytics-card').then(mod => mod.AnalyticsCard), {
  ssr: false,
  loading: () => <Skeleton className="h-[126px]" />,
});


export default function NdrfDashboard() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [stats, setStats] = useState({
      floodAlerts: "0",
      landslideAlerts: "0",
      resourcesDeployed: "12 Units"
  });

  useEffect(() => {
    const fetchNdrfData = async () => {
      try {
        const ndrfAlerts = await getAlerts("NDRF");
        setAlerts(ndrfAlerts);

        const floodAlertsCount = ndrfAlerts.filter(a => a.type.toLowerCase().includes('flood')).length;
        const landslideAlertsCount = ndrfAlerts.filter(a => a.type.toLowerCase().includes('landslide')).length;

        setStats(prevStats => ({
            ...prevStats,
            floodAlerts: floodAlertsCount.toString(),
            landslideAlerts: landslideAlertsCount.toString(),
        }));
      } catch (error) {
        console.error("Failed to fetch NDRF data:", error);
      }
    };
    fetchNdrfData();
  }, []);
  
  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">NDRF Organization Dashboard</h2>
      <div className="grid gap-4 md:grid-cols-3">
        <AnalyticsCard title="Predictive Flood Alerts" icon={Waves} value={stats.floodAlerts} />
        <AnalyticsCard title="Active Landslide Warnings" icon={Mountain} value={stats.landslideAlerts} />
        <AnalyticsCard title="Resources Deployed" icon={Truck} value={stats.resourcesDeployed} />
      </div>
      <div className="grid grid-cols-1 gap-4">
        <AlertFeed alerts={alerts.map(a => ({...a, time: a.date || 'N/A', details: a.description}))} viewAllLink="/ndrf/alerts" />
      </div>
    </div>
  );
}
