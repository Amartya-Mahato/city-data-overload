
"use client";

import { usePathname } from 'next/navigation';
import Link from 'next/link';
import {
  Sidebar,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarFooter,
  SidebarTrigger,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarContent,
  SidebarMenuSubItem
} from '@/components/ui/sidebar';
import { 
  Shield, 
  Siren, 
  Building, 
  Flame, 
  Waves, 
  TrafficCone, 
  Gavel, 
  Users, 
  Hospital,
  Settings,
  LogOut,
  Bot,
  LayoutDashboard,
  Megaphone,
  FileText,
  Trash2,
  Wrench,
  FlameIcon,
  Truck,
  Mountain,
  Car,
  CheckCircle2,
  List,
  AlertTriangle,
  ShieldAlert
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Collapsible, CollapsibleTrigger, CollapsibleContent } from '@/components/ui/collapsible';
import { cn } from '@/lib/utils';


const allNavItems = [
  { 
    href: '/super-admin', 
    icon: Shield, 
    label: 'Super Admin',
    subItems: [
        { href: '/super-admin', icon: LayoutDashboard, label: 'Dashboard' },
        { href: '/super-admin/alerts', icon: AlertTriangle, label: 'All Alerts' },
    ]
  },
  { 
    href: '/police', 
    icon: Siren, 
    label: 'Police Dept.',
    subItems: [
        { href: '/police', icon: LayoutDashboard, label: 'Dashboard' },
        { href: '/police/protests', icon: Megaphone, label: 'Active Protests' },
        { href: '/police/incidents', icon: FileText, label: 'Incident Reports' },
        { href: '/police/escalated', icon: ShieldAlert, label: 'Escalated Alerts' },
    ]
  },
  { 
    href: '/bmc', 
    icon: Building, 
    label: 'BMC (Civic)',
    subItems: [
        { href: '/bmc', icon: LayoutDashboard, label: 'Dashboard' },
        { href: '/bmc/garbage', icon: Trash2, label: 'Garbage Tickets' },
        { href: '/bmc/potholes', icon: Wrench, label: 'Pothole Reports' },
        { href: '/bmc/escalated', icon: ShieldAlert, label: 'Escalated Alerts' },
    ]
  },
  { 
    href: '/fire', 
    icon: Flame, 
    label: 'Fire Dept.',
    subItems: [
        { href: '/fire', icon: LayoutDashboard, label: 'Dashboard' },
        { href: '/fire/incidents', icon: FlameIcon, label: 'Active Incidents' },
        { href: '/fire/units', icon: Truck, label: 'Unit Status' },
        { href: '/fire/escalated', icon: ShieldAlert, label: 'Escalated Alerts' },
    ]
  },
  { 
    href: '/ndrf', 
    icon: Waves, 
    label: 'NDRF Org.',
    subItems: [
        { href: '/ndrf', icon: LayoutDashboard, label: 'Dashboard' },
        { href: '/ndrf/alerts', icon: Mountain, label: 'Disaster Alerts' },
        { href: '/ndrf/resources', icon: List, label: 'Resource Inventory' },
        { href: '/ndrf/escalated', icon: ShieldAlert, label: 'Escalated Alerts' },
    ]
  },
  { 
    href: '/traffic', 
    icon: TrafficCone, 
    label: 'Traffic Control',
    subItems: [
        { href: '/traffic', icon: LayoutDashboard, label: 'Dashboard' },
        { href: '/traffic/congestion', icon: Car, label: 'Congestion Hotspots' },
        { href: '/traffic/reroutes', icon: TrafficCone, label: 'Active Reroutes' },
        { href: '/traffic/escalated', icon: ShieldAlert, label: 'Escalated Alerts' },
    ]
  },
  { 
    href: '/moderator', 
    icon: Gavel, 
    label: 'Moderator',
    subItems: [
        { href: '/moderator', icon: LayoutDashboard, label: 'Dashboard' },
        { href: '/moderator/queue', icon: List, label: 'Pending Queue' },
        { href: '/moderator/approved', icon: CheckCircle2, label: 'Approved Content' },
        { href: '/moderator/escalated', icon: ShieldAlert, label: 'Escalated Alerts' },
    ]
  },
  { 
    href: '/community', 
    icon: Users, 
    label: 'Community',
    subItems: [
        { href: '/community', icon: LayoutDashboard, label: 'Dashboard' },
        { href: '/community/events', icon: List, label: 'Events List' },
        { href: '/community/volunteers', icon: Users, label: 'Volunteers' },
        { href: '/community/escalated', icon: ShieldAlert, label: 'Escalated Alerts' },
    ]
  },
  { 
    href: '/hospitals', 
    icon: Hospital, 
    label: 'Hospitals',
    subItems: [
        { href: '/hospitals', icon: LayoutDashboard, label: 'Dashboard' },
        { href: '/hospitals/alerts', icon: Siren, label: 'Incident Alerts' },
        { href: '/hospitals/activations', icon: Truck, label: 'ER Activations' },
        { href: '/hospitals/escalated', icon: ShieldAlert, label: 'Escalated Alerts' },
    ]
  },
];

const userDetails: { [key: string]: { name: string, email: string, fallback: string } } = {
  '/super-admin': { name: 'Admin User', email: 'super.admin@blr.gov.in', fallback: 'SA' },
  '/police': { name: 'Police Officer', email: 'police@blr.gov.in', fallback: 'PO' },
  '/bmc': { name: 'BMC Official', email: 'bmc@blr.gov.in', fallback: 'BO' },
  '/fire': { name: 'Firefighter', email: 'fire@blr.gov.in', fallback: 'FF' },
  '/ndrf': { name: 'NDRF Responder', email: 'ndrf@blr.gov.in', fallback: 'NR' },
  '/traffic': { name: 'Traffic Officer', email: 'traffic@blr.gov.in', fallback: 'TO' },
  '/moderator': { name: 'Moderator', email: 'moderator@blr.gov.in', fallback: 'M' },
  '/community': { name: 'Community Admin', email: 'community@blr.gov.in', fallback: 'CA' },
  '/hospitals': { name: 'Hospital Staff', email: 'hospital@blr.gov.in', fallback: 'HS' },
};


export function AppSidebar() {
  const pathname = usePathname();

  if (pathname === '/') {
    return null;
  }

  const isSuperAdmin = pathname.startsWith('/super-admin');
  const currentTopLevelPath = '/' + (pathname.split('/')[1] || '');
  
  const navItems = isSuperAdmin ? allNavItems : allNavItems.filter(item => item.href === currentTopLevelPath);
  const currentUser = userDetails[currentTopLevelPath] || { name: 'Guest User', email: 'guest@example.com', fallback: 'GU' };


  return (
    <Sidebar>
      <SidebarHeader>
        <div className="flex items-center gap-2">
            <Button variant="ghost" size="icon" className="h-8 w-8 shrink-0 rounded-full bg-primary text-primary-foreground flex items-center justify-center">
                <Bot className="h-5 w-5"/>
            </Button>
            <div className="flex flex-col group-data-[collapsible=icon]:hidden">
                <h2 className="text-lg font-semibold tracking-tight">Namma Nagara</h2>
                <p className="text-xs text-muted-foreground">Bengaluru Division</p>
            </div>
        </div>
      </SidebarHeader>

      <SidebarContent>
        <SidebarMenu className="flex-1 p-2">
            {navItems.map((item) => (
            <SidebarMenuItem key={item.href}>
                {!item.subItems ? (
                <Link href={item.href} passHref>
                    <SidebarMenuButton
                    isActive={pathname === item.href}
                    icon={<item.icon />}
                    tooltip={item.label}
                    >
                    <span>{item.label}</span>
                    </SidebarMenuButton>
                </Link>
                ) : (
                <Collapsible defaultOpen={pathname.startsWith(item.href)}>
                    <CollapsibleTrigger asChild>
                         <SidebarMenuButton
                            icon={<item.icon />}
                            tooltip={item.label}
                            className={cn("w-full", isSuperAdmin && item.href !== '/super-admin' && "hidden")}
                         >
                            <span>{item.label}</span>
                        </SidebarMenuButton>
                    </CollapsibleTrigger>
                    <CollapsibleContent>
                        <SidebarMenuSub>
                            {item.subItems.map((subItem) => (
                                <SidebarMenuSubItem key={subItem.href}>
                                    <Link href={subItem.href} passHref>
                                        <SidebarMenuSubButton isActive={pathname === subItem.href}>
                                                <subItem.icon/>
                                                <span>{subItem.label}</span>
                                        </SidebarMenuSubButton>
                                    </Link>
                                </SidebarMenuSubItem>
                            ))}
                        </SidebarMenuSub>
                    </CollapsibleContent>
                </Collapsible>
                )}
            </SidebarMenuItem>
            ))}
        </SidebarMenu>
      </SidebarContent>
      
      <SidebarFooter className="p-2">
         <SidebarMenu>
           <SidebarMenuItem>
              <Link href="/" passHref>
                <SidebarMenuButton icon={<LogOut />} tooltip="Log Out">
                  Log Out
                </SidebarMenuButton>
              </Link>
          </SidebarMenuItem>
        </SidebarMenu>
        <div className="flex items-center gap-2 p-2 border-t mt-2">
            <Avatar className="h-9 w-9">
                <AvatarImage src="https://placehold.co/100x100.png" alt={currentUser.name} data-ai-hint="person face" />
                <AvatarFallback>{currentUser.fallback}</AvatarFallback>
            </Avatar>
            <div className="flex flex-col group-data-[collapsible=icon]:hidden">
                <p className="text-sm font-medium">{currentUser.name}</p>
                <p className="text-xs text-muted-foreground">{currentUser.email}</p>
            </div>
        </div>
         <div className="mt-2 group-data-[collapsible=icon]:hidden">
            <SidebarTrigger className="w-full justify-start" />
        </div>
      </SidebarFooter>
    </Sidebar>
  );
}
