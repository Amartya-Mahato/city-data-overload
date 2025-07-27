
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
import { FlameIcon, MoreHorizontal } from "lucide-react";
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
import type { Alert } from "@/lib/data";
import { Skeleton } from "@/components/ui/skeleton";

type Incident = Alert & {
  severity: "Critical" | "High" | "Medium" | "Low";
  status: "Dispatched" | "En-route" | "On-Scene" | "Resolved";
};

export default function ActiveIncidentsPage() {
  const [incidents, setIncidents] = React.useState<Incident[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const [selectedIncident, setSelectedIncident] = React.useState<Incident | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = React.useState(false);
  const { toast } = useToast();

  React.useEffect(() => {
    const fetchIncidents = async () => {
      setIsLoading(true);
      try {
        const fetchedAlerts = await getAlerts('Fire');
        setIncidents(fetchedAlerts.map(a => ({
          ...a,
          severity: a.severity as Incident['severity'],
          status: a.status as Incident['status'],
        })));
      } catch (error) {
         console.error("Failed to fetch incidents:", error);
         toast({
          title: "Error",
          description: "Could not fetch incidents from the database.",
          variant: "destructive",
        });
      } finally {
        setIsLoading(false);
      }
    }
    fetchIncidents();
  }, [toast]);

  const handleViewDetails = (incident: Incident) => {
    setSelectedIncident(incident);
    setIsDetailsOpen(true);
  };

  const handleDispatchUnit = (incidentId: string) => {
    toast({
      title: "Unit Dispatched",
      description: `An additional unit has been dispatched to incident #${incidentId}.`,
    });
  };

  return (
    <>
      <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
        <h2 className="text-3xl font-bold tracking-tight">Active Incidents</h2>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><FlameIcon /> Live Fire Incidents</CardTitle>
            <CardDescription>A real-time log of all active fire-related incidents from the database.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Incident ID</TableHead>
                  <TableHead>Location</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Severity</TableHead>
                  <TableHead>Status</TableHead>
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
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell className="text-right"><Skeleton className="h-8 w-8" /></TableCell>
                    </TableRow>
                  ))
                ) : (
                  incidents.map((incident) => (
                    <TableRow key={incident.id}>
                      <TableCell className="font-medium">{incident.id}</TableCell>
                      <TableCell>{incident.location}</TableCell>
                      <TableCell>{incident.type}</TableCell>
                      <TableCell>
                        <Badge variant={incident.severity === 'Critical' || incident.severity === 'High' ? 'destructive' : 'default'}>
                          {incident.severity}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant={incident.status === 'Resolved' ? 'secondary' : 'default'}>
                          {incident.status}
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
                            <DropdownMenuItem onClick={() => handleViewDetails(incident)}>View Details</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleDispatchUnit(incident.id)}>Dispatch Additional Unit</DropdownMenuItem>
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
            <DialogTitle>Incident Details: {selectedIncident?.id}</DialogTitle>
            <DialogDescription>
              A detailed view of the fire incident.
            </DialogDescription>
          </DialogHeader>
          {selectedIncident && (
            <div className="space-y-4 py-4">
               <div>
                <h4 className="font-semibold">Location</h4>
                <p>{selectedIncident.location}</p>
              </div>
               <div>
                <h4 className="font-semibold">Type</h4>
                <p>{selectedIncident.type}</p>
              </div>
              <div>
                <h4 className="font-semibold">Description</h4>
                <p className="text-muted-foreground">{selectedIncident.description}</p>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
