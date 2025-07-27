
"use client";

import * as React from "react";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Mountain, MoreHorizontal } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { getAlerts } from "@/services/firestore";
import type { Alert } from "@/lib/data";
import { useToast } from "@/hooks/use-toast";
import { Skeleton } from "@/components/ui/skeleton";

export default function DisasterAlertsPage() {
  const [alerts, setAlerts] = React.useState<Alert[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const { toast } = useToast();

  React.useEffect(() => {
    const fetchDisasterAlerts = async () => {
      setIsLoading(true);
      try {
        const ndrfAlerts = await getAlerts('NDRF');
        setAlerts(ndrfAlerts);
      } catch (error) {
        console.error("Failed to fetch disaster alerts:", error);
        toast({
          title: "Error",
          description: "Could not fetch disaster alerts from the database.",
          variant: "destructive",
        });
      } finally {
        setIsLoading(false);
      }
    };
    fetchDisasterAlerts();
  }, [toast]);

  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Disaster Alerts</h2>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Mountain /> Predictive & Active Disaster Alerts</CardTitle>
          <CardDescription>A log of all natural disaster warnings and alerts from the database.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Alert ID</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Location</TableHead>
                <TableHead>Severity</TableHead>
                <TableHead>Date Issued</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                Array.from({ length: 3 }).map((_, i) => (
                  <TableRow key={i}>
                    <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                    <TableCell className="text-right"><Skeleton className="h-8 w-8" /></TableCell>
                  </TableRow>
                ))
              ) : (
                alerts.map((alert) => (
                  <TableRow key={alert.id}>
                    <TableCell className="font-medium">{alert.id}</TableCell>
                    <TableCell>{alert.type}</TableCell>
                    <TableCell>{alert.location}</TableCell>
                    <TableCell>
                      <Badge variant={alert.severity === 'High' || alert.severity === 'Critical' ? 'destructive' : 'default'}>
                        {alert.severity}
                      </Badge>
                    </TableCell>
                    <TableCell>{alert.date}</TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" className="h-8 w-8 p-0">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem>View Details</DropdownMenuItem>
                          <DropdownMenuItem>Deploy Resources</DropdownMenuItem>
                          <DropdownMenuItem>Initiate Evacuation Plan</DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
