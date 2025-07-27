
"use client";

import { useState, useEffect } from "react";
import { AlertFeed } from "@/components/dashboards/alert-feed";
import { Megaphone, ShieldAlert, Users } from "lucide-react";
import { ProtestHeatmapChart } from "@/components/dashboards/protest-heatmap-chart";
import { getAlerts } from "@/services/firestore";
import type { Alert } from "@/lib/data";
import dynamic from 'next/dynamic';
import { Skeleton } from '@/components/ui/skeleton';
import { BroadcastAlertCard } from "@/components/dashboards/broadcast-alert-card";

const AnalyticsCard = dynamic(() => import('@/components/dashboards/analytics-card').then(mod => mod.AnalyticsCard), {
  ssr: false,
  loading: () => <Skeleton className="h-[126px]" />,
});

export default function PoliceDashboard() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [stats, setStats] = useState({
      activeProtests: '0',
      highPriority: '0',
      personnelDeployed: '128'
  });

  useEffect(() => {
    const fetchPoliceData = async () => {
      try {
        const policeAlerts = await getAlerts("Police");
        setAlerts(policeAlerts);

        const activeProtestsCount = policeAlerts.filter(a => a.type === 'Protest' && a.status !== 'Resolved').length;
        const highPriorityCount = policeAlerts.filter(a => a.urgency === 'High' || a.urgency === 'Critical').length;

        setStats(prevStats => ({
            ...prevStats,
            activeProtests: activeProtestsCount.toString(),
            highPriority: highPriorityCount.toString(),
        }));

      } catch (error) {
        console.error("Failed to fetch police data:", error);
      }
    };
    fetchPoliceData();
  }, []);

  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Police Department Dashboard</h2>
      <div className="grid gap-4 md:grid-cols-3">
        <AnalyticsCard title="Active Protests" icon={Megaphone} value={stats.activeProtests} />
        <AnalyticsCard title="High-Priority Alerts" icon={ShieldAlert} value={stats.highPriority} />
        <AnalyticsCard title="Personnel Deployed" icon={Users} value={stats.personnelDeployed} />
      </div>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-7">
        <div className="lg:col-span-4 grid grid-cols-1 gap-4">
            <AlertFeed alerts={alerts.map(a => ({...a, time: a.date || 'N/A', details: a.description}))} viewAllLink="/police/incidents" />
        </div>
        <div className="lg:col-span-3 grid grid-cols-1 gap-4">
            <ProtestHeatmapChart />
            <BroadcastAlertCard />
        </div>
      </div>
    </div>
  );
}
