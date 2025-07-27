
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
import { TrafficCone, MoreHorizontal } from "lucide-react";
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

type Reroute = {
  id: string;
  location: string;
  reason: string;
  status: "Active" | "Inactive" | "Planned";
  startTime: string;
  details: string;
};

const initialReroutes: Reroute[] = [
  { id: "RR-001", location: "Silk Board", reason: "VIP Movement", status: "Active", startTime: "08:30 AM", details: "Reroute active due to the movement of a high-profile convoy. Expected to last 2 hours." },
  { id: "RR-002", location: "Old Airport Road", reason: "Tree Fallen", status: "Active", startTime: "09:00 AM", details: "A large tree has fallen and is blocking both lanes. Civic teams are on site." },
  { id: "RR-003", location: "MG Road", reason: "Protest", status: "Inactive", startTime: "07:00 AM", details: "Reroute was active this morning due to a protest march. It has now been cleared." },
];

export default function ActiveReroutesPage() {
  const [reroutes, setReroutes] = React.useState<Reroute[]>(initialReroutes);
  const [selectedReroute, setSelectedReroute] = React.useState<Reroute | null>(null);
  const [isDetailsOpen, setIsDetailsOpen] = React.useState(false);
  const { toast } = useToast();

  const handleViewDetails = (reroute: Reroute) => {
    setSelectedReroute(reroute);
    setIsDetailsOpen(true);
  };

  const handleAction = (action: string, rerouteId: string) => {
    toast({
      title: "Action Initiated",
      description: `${action} for reroute #${rerouteId}.`,
    });
  };

  return (
    <>
      <div className="flex-1 space-y-4 p-4 md:p-8 pt-6">
        <h2 className="text-3xl font-bold tracking-tight">Active Reroutes</h2>
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2"><TrafficCone /> Current Traffic Reroutes</CardTitle>
            <CardDescription>A list of all active and planned traffic reroutes.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Reroute ID</TableHead>
                  <TableHead>Location</TableHead>
                  <TableHead>Reason</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Start Time</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {reroutes.map((reroute) => (
                  <TableRow key={reroute.id}>
                    <TableCell className="font-medium">{reroute.id}</TableCell>
                    <TableCell>{reroute.location}</TableCell>
                    <TableCell>{reroute.reason}</TableCell>
                    <TableCell>
                      <Badge variant={reroute.status === 'Active' ? 'destructive' : 'secondary'}>
                          {reroute.status}
                      </Badge>
                    </TableCell>
                    <TableCell>{reroute.startTime}</TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" className="h-8 w-8 p-0">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => handleViewDetails(reroute)}>View Details</DropdownMenuItem>
                          <DropdownMenuItem onClick={() => handleAction("Reroute Modification", reroute.id)}>Modify Reroute</DropdownMenuItem>
                          <DropdownMenuItem onClick={() => handleAction("Public Notification Sent", reroute.id)}>Notify Public</DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>

      <Dialog open={isDetailsOpen} onOpenChange={setIsDetailsOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reroute Details: {selectedReroute?.id}</DialogTitle>
            <DialogDescription>
              A detailed view of the reroute information.
            </DialogDescription>
          </DialogHeader>
          {selectedReroute && (
            <div className="space-y-4 py-4">
               <div>
                <h4 className="font-semibold">Location</h4>
                <p>{selectedReroute.location}</p>
              </div>
               <div>
                <h4 className="font-semibold">Reason</h4>
                <p>{selectedReroute.reason}</p>
              </div>
              <div>
                <h4 className="font-semibold">Full Details</h4>
                <p className="text-muted-foreground">{selectedReroute.details}</p>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
