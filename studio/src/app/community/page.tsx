
"use client";

import { useState, useEffect } from "react";
import { AlertFeed } from "@/components/dashboards/alert-feed";
import { PawPrint, CalendarDays, Users } from "lucide-react";
import { getAlerts, getEvents } from "@/services/firestore";
import type { Alert } from "@/lib/data";
import dynamic from 'next/dynamic';
import { Skeleton } from '@/components/ui/skeleton';

const AnalyticsCard = dynamic(() => import('@/components/dashboards/analytics-card').then(mod => mod.AnalyticsCard), {
  ssr: false,
  loading: () => <Skeleton className="h-[126px]" />,
});

export default function CommunityDashboard() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [stats, setStats] = useState({
      lostAndFound: "0",
      upcomingEvents: "0",
      activeVolunteers: "56"
  });

  useEffect(() => {
    const fetchCommunityData = async () => {
      try {
        const communityAlerts = await getAlerts("Community");
        setAlerts(communityAlerts);
        
        const events = await getEvents();

        const lostAndFoundCount = communityAlerts.filter(a => a.type.toLowerCase().includes('lost')).length;
        const upcomingEventsCount = events.filter(e => e.status === 'Upcoming').length;

        setStats(prevStats => ({
            ...prevStats,
            lostAndFound: lostAndFoundCount.toString(),
            upcomingEvents: upcomingEventsCount.toString(),
        }));

      } catch (error) {
        console.error("Failed to fetch community data:", error);
      }
    };
    fetchCommunityData();
  }, []);

  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Community Admin Dashboard</h2>
      <div className="grid gap-4 md:grid-cols-3">
        <AnalyticsCard title="Lost & Found Reports" icon={PawPrint} value={stats.lostAndFound} />
        <AnalyticsCard title="Upcoming Events" icon={CalendarDays} value={stats.upcomingEvents} />
        <AnalyticsCard title="Active Volunteers" icon={Users} value={stats.activeVolunteers} />
      </div>
       <div className="grid grid-cols-1 gap-4">
        <AlertFeed alerts={alerts.map(a => ({...a, time: a.date || 'N/A', details: a.description}))} viewAllLink="/community/events" />
      </div>
    </div>
  );
}
