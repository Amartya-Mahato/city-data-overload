
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
import { Siren, MoreHorizontal } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { getAlerts } from "@/services/firestore";
import type { Alert as AlertType } from "@/lib/data";
import { Skeleton } from "@/components/ui/skeleton";

type Alert = AlertType & {
  urgency: "Critical" | "High" | "Medium" | "Low";
  status: "New" | "ER Activated" | "Ambulance En-route" | "Closed";
};

export default function IncidentAlertsPage() {
  const [alerts, setAlerts] = React.useState<Alert[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const [selectedAlert, setSelectedAlert] = React.useState<Alert | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = React.useState(false);
  const { toast } = useToast();

  React.useEffect(() => {
    const fetchAlerts = async () => {
      setIsLoading(true);
      try {
        const fetchedAlerts = await getAlerts('Hospitals');
        setAlerts(fetchedAlerts.map(a => ({
          ...a,
          urgency: a.urgency as Alert['urgency'],
          status: a.status as Alert['status'],
        })));
      } catch (error) {
         console.error("Failed to fetch alerts:", error);
         toast({
          title: "Error",
          description: "Could not fetch alerts from the database.",
          variant: "destructive",
        });
      } finally {
        setIsLoading(false);
      }
    };
    fetchAlerts();
  }, [toast]);

  const handleViewDetails = (alert: Alert) => {
    setSelectedAlert(alert);
    setIsDetailsOpen(true);
  };

  const handleActivateER = (alertId: string) => {
    toast({
      title: "ER Protocol Activated",
      description: `All nearby hospitals have been notified to prepare for incoming patients for alert #${alertId}.`,
      variant: "destructive",
    });
  };

  const handleDispatchAmbulance = (alertId: string) => {
    toast({
      title: "Ambulance Dispatched",
      description: `An ambulance has been dispatched for alert #${alertId}.`,
    });
  };

  return (
    <>
      <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
        <h2 className="text-3xl font-bold tracking-tight">Incident Alerts</h2>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><Siren /> Live Medical & Accident Alerts</CardTitle>
            <CardDescription>A log of incoming alerts requiring medical attention from the database.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Alert ID</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Location</TableHead>
                  <TableHead>Urgency</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading ? (
                  Array.from({ length: 3 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-28" /></TableCell>
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
                        <Badge variant={alert.urgency === 'Critical' || alert.urgency === 'High' ? 'destructive' : 'default'}>
                          {alert.urgency}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant={alert.status === 'New' ? 'destructive' : alert.status === 'Closed' ? 'secondary' : 'default'}>
                          {alert.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" className="h-8 w-8 p-0">
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => handleViewDetails(alert)}>View Details</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleActivateER(alert.id)}>Activate ER Protocol</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleDispatchAmbulance(alert.id)}>Dispatch Ambulance</DropdownMenuItem>
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

      <Dialog open={isDetailsOpen} onOpenChange={setIsDetailsOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Alert Details: {selectedAlert?.id}</DialogTitle>
            <DialogDescription>
              A detailed view of the alert information.
            </DialogDescription>
          </DialogHeader>
          {selectedAlert && (
            <div className="space-y-4 py-4">
               <div>
                <h4 className="font-semibold">Location</h4>
                <p>{selectedAlert.location}</p>
              </div>
               <div>
                <h4 className="font-semibold">Type</h4>
                <p>{selectedAlert.type}</p>
              </div>
              <div>
                <h4 className="font-semibold">Description</h4>
                <p className="text-muted-foreground">{selectedAlert.description}</p>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
