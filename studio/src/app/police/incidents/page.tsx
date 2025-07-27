
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
import { FileText, MoreHorizontal } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useToast } from "@/hooks/use-toast";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { getAlerts, updateAlertStatus } from "@/services/firestore";
import type { Alert } from "@/lib/data";
import { Skeleton } from "@/components/ui/skeleton";

type Incident = Alert & {
  status: "New" | "Investigating" | "Resolved";
};

export default function IncidentReportsPage() {
  const [incidents, setIncidents] = React.useState<Incident[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const [selectedIncident, setSelectedIncident] = React.useState<Incident | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = React.useState(false);
  const [isUpdateOpen, setIsUpdateOpen] = React.useState(false);
  const [newStatus, setNewStatus] = React.useState<Incident['status'] | null>(null);
  const { toast } = useToast();
  
  React.useEffect(() => {
    const fetchIncidents = async () => {
      setIsLoading(true);
      try {
        const fetchedAlerts = await getAlerts('Police');
        setIncidents(fetchedAlerts.map(a => ({...a, status: a.status as Incident['status']})));
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

  const handleUpdateStatus = (incident: Incident) => {
    setSelectedIncident(incident);
    setNewStatus(incident.status);
    setIsUpdateOpen(true);
  };

  const confirmUpdateStatus = async () => {
    if (selectedIncident && newStatus) {
      try {
        await updateAlertStatus(selectedIncident.id, newStatus);
        setIncidents(prevIncidents =>
          prevIncidents.map(i =>
            i.id === selectedIncident.id ? { ...i, status: newStatus } : i
          )
        );
        toast({
          title: "Status Updated",
          description: `Incident #${selectedIncident.id} status has been updated to "${newStatus}".`,
        });
      } catch (error) {
        console.error("Failed to update status:", error);
        toast({
          title: "Error",
          description: "Could not update incident status.",
          variant: "destructive",
        });
      }
    }
    setIsUpdateOpen(false);
    setSelectedIncident(null);
  };
  
  const handleNotify = (incidentId: string) => {
    toast({
      title: "Notification Sent",
      description: `Email alert for incident ${incidentId} has been sent to nearby hospitals.`,
    });
  };

  const handleAssignUnit = (incidentId: string) => {
    toast({
      title: "Unit Assigned",
      description: `A unit has been assigned to incident #${incidentId}.`,
    });
  };

  const getStatusBadgeVariant = (status: Incident['status']) => {
    switch (status) {
      case 'Resolved': return 'secondary';
      case 'New': return 'destructive';
      case 'Investigating': return 'default';
      default: return 'default';
    }
  };

  return (
    <>
      <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
        <h2 className="text-3xl font-bold tracking-tight">Incident Reports</h2>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><FileText /> All Incidents</CardTitle>
            <CardDescription>A comprehensive log of all reported incidents from the database.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Report ID</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Location</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading ? (
                  Array.from({ length: 4 }).map((_, i) => (
                    <TableRow key={i}>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-20" /></TableCell>
                      <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                      <TableCell className="text-right"><Skeleton className="h-8 w-8" /></TableCell>
                    </TableRow>
                  ))
                ) : (
                  incidents.map((incident) => (
                    <TableRow key={incident.id}>
                      <TableCell className="font-medium">{incident.id}</TableCell>
                      <TableCell>{incident.type}</TableCell>
                      <TableCell>{incident.location}</TableCell>
                      <TableCell>
                        <Badge variant={getStatusBadgeVariant(incident.status)}>
                          {incident.status}
                        </Badge>
                      </TableCell>
                      <TableCell>{incident.date}</TableCell>
                      <TableCell className="text-right">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" className="h-8 w-8 p-0">
                              <span className="sr-only">Open menu</span>
                              <MoreHorizontal className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => handleViewDetails(incident)}>View Details</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleAssignUnit(incident.id)}>Assign Unit</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleNotify(incident.id)}>Notify Hospitals</DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleUpdateStatus(incident)}>Update Status</DropdownMenuItem>
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
              A detailed view of the incident report.
            </DialogDescription>
          </DialogHeader>
          {selectedIncident && (
            <div className="space-y-4 py-4">
               <div>
                <h4 className="font-semibold">Type</h4>
                <p>{selectedIncident.type}</p>
              </div>
               <div>
                <h4 className="font-semibold">Location</h4>
                <p>{selectedIncident.location}</p>
              </div>
              <div>
                <h4 className="font-semibold">Description</h4>
                <p className="text-muted-foreground">{selectedIncident.description}</p>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
      
      <AlertDialog open={isUpdateOpen} onOpenChange={setIsUpdateOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Update Incident Status</AlertDialogTitle>
            <AlertDialogDescription>
              Select the new status for incident <strong>{selectedIncident?.id}</strong>.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="py-4">
            <Select onValueChange={(value: Incident['status']) => setNewStatus(value)} defaultValue={selectedIncident?.status}>
                <SelectTrigger>
                    <SelectValue placeholder="Select a status" />
                </SelectTrigger>
                <SelectContent>
                    <SelectItem value="New">New</SelectItem>
                    <SelectItem value="Investigating">Investigating</SelectItem>
                    <SelectItem value="Resolved">Resolved</SelectItem>
                </SelectContent>
            </Select>
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={confirmUpdateStatus}>Update</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
