
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
import { ShieldAlert } from "lucide-react";
import { getAlerts } from "@/services/firestore";
import type { Alert } from "@/lib/data";
import { useToast } from "@/hooks/use-toast";
import { Skeleton } from "@/components/ui/skeleton";

export default function FireEscalatedAlertsPage() {
  const [escalatedAlerts, setEscalatedAlerts] = React.useState<Alert[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const { toast } = useToast();

  React.useEffect(() => {
    const fetchEscalated = async () => {
      setIsLoading(true);
      try {
        const fireAlerts = await getAlerts('Fire');
        setEscalatedAlerts(fireAlerts.filter(a => a.isEscalated));
      } catch (error) {
        console.error("Failed to fetch escalated alerts:", error);
        toast({
          title: "Error",
          description: "Could not fetch escalated alerts from the database.",
          variant: "destructive",
        });
      } finally {
        setIsLoading(false);
      }
    };
    fetchEscalated();
  }, [toast]);

  return (
    <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
      <h2 className="text-3xl font-bold tracking-tight">Escalated Alerts</h2>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><ShieldAlert /> Alerts Escalated by Super Admin</CardTitle>
          <CardDescription>High-priority alerts requiring immediate attention from the Fire Department.</CardDescription>
        </CardHeader>
        <CardContent>
           {isLoading ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Alert ID</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Location</TableHead>
                  <TableHead>Urgency</TableHead>
                  <TableHead>Description</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {Array.from({ length: 2 }).map((_, i) => (
                  <TableRow key={i}>
                    <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-48" /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
           ) : escalatedAlerts.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Alert ID</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Location</TableHead>
                  <TableHead>Urgency</TableHead>
                  <TableHead>Description</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {escalatedAlerts.map((alert) => (
                  <TableRow key={alert.id}>
                    <TableCell className="font-medium">{alert.id}</TableCell>
                    <TableCell>{alert.type}</TableCell>
                    <TableCell>{alert.location}</TableCell>
                    <TableCell>
                      <Badge variant={alert.urgency === 'Critical' || alert.urgency === 'High' ? 'destructive' : 'default'}>
                        {alert.urgency}
                      </Badge>
                    </TableCell>
                    <TableCell>{alert.description}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
             <p className="text-muted-foreground">No alerts have been escalated to this department yet.</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
