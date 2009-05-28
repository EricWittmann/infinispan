/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.marshall.jboss;

import org.infinispan.commands.RemoteCommandFactory;
import org.infinispan.commands.ReplicableCommand;
import org.infinispan.commands.control.StateTransferControlCommand;
import org.infinispan.commands.read.GetKeyValueCommand;
import org.infinispan.commands.remote.ClusteredGetCommand;
import org.infinispan.commands.remote.MultipleRpcCommand;
import org.infinispan.commands.tx.CommitCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.tx.RollbackCommand;
import org.infinispan.commands.write.ClearCommand;
import org.infinispan.commands.write.InvalidateCommand;
import org.infinispan.commands.write.InvalidateL1Command;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.commands.write.PutMapCommand;
import org.infinispan.commands.write.RemoveCommand;
import org.infinispan.commands.write.ReplaceCommand;
import org.infinispan.container.entries.ImmortalCacheEntry;
import org.infinispan.container.entries.ImmortalCacheValue;
import org.infinispan.container.entries.InternalEntryFactory;
import org.infinispan.container.entries.MortalCacheEntry;
import org.infinispan.container.entries.MortalCacheValue;
import org.infinispan.container.entries.TransientCacheEntry;
import org.infinispan.container.entries.TransientCacheValue;
import org.infinispan.container.entries.TransientMortalCacheEntry;
import org.infinispan.container.entries.TransientMortalCacheValue;
import org.infinispan.loaders.bucket.Bucket;
import org.infinispan.marshall.MarshalledValue;
import org.infinispan.remoting.responses.ExtendedResponse;
import org.infinispan.remoting.responses.RequestIgnoredResponse;
import org.infinispan.remoting.responses.SuccessfulResponse;
import org.infinispan.remoting.responses.UnsuccessfulResponse;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.jgroups.JGroupsAddress;
import org.infinispan.statetransfer.Person;
import org.infinispan.transaction.TransactionLog;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.util.FastCopyHashMap;
import org.infinispan.util.Immutables;
import org.jgroups.stack.IpAddress;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.*;

/**
 * JBossMarshallingMarshallerTest.
 * <p/>
 * Todo: AtomicHashMap missing.
 *
 * @author Galder Zamarreño
 * @since 4.0
 */
@Test(groups = "functional", testName = "marshall.jboss.JBossMarshallerTest")
public class JBossMarshallerTest {

   private final JBossMarshaller marshaller = new JBossMarshaller();

   @BeforeTest
   public void setUp() {
      marshaller.init(Thread.currentThread().getContextClassLoader(), new RemoteCommandFactory());
   }

   @AfterTest
   public void tearDown() {
      marshaller.stop();
   }

   public void testJGroupsAddressMarshalling() throws Exception {
      JGroupsAddress address = new JGroupsAddress(new IpAddress(12345));
      marshallAndAssertEquality(address);
   }

   public void testGlobalTransactionMarshalling() throws Exception {
      JGroupsAddress jGroupsAddress = new JGroupsAddress(new IpAddress(12345));
      GlobalTransaction gtx = new GlobalTransaction(jGroupsAddress, false);
      marshallAndAssertEquality(gtx);
   }

   public void testListMarshalling() throws Exception {
      List l1 = new ArrayList();
      List l2 = new LinkedList();
      for (int i = 0; i < 10; i++) {
         JGroupsAddress jGroupsAddress = new JGroupsAddress(new IpAddress(1000 * i));
         GlobalTransaction gtx = new GlobalTransaction(jGroupsAddress, false);
         l1.add(gtx);
         l2.add(gtx);
      }
      marshallAndAssertEquality(l1);
      marshallAndAssertEquality(l2);
   }

   public void testMapMarshalling() throws Exception {
      Map m1 = new HashMap();
      Map m2 = new TreeMap();
      Map m3 = new HashMap();
      Map<Integer, GlobalTransaction> m4 = new FastCopyHashMap<Integer, GlobalTransaction>();
      for (int i = 0; i < 10; i++) {
         JGroupsAddress jGroupsAddress = new JGroupsAddress(new IpAddress(1000 * i));
         GlobalTransaction gtx = new GlobalTransaction(jGroupsAddress, false);
         m1.put(1000 * i, gtx);
         m2.put(1000 * i, gtx);
         m4.put(1000 * i, gtx);
      }
      Map m5 = Immutables.immutableMapWrap(m3);
      marshallAndAssertEquality(m1);
      marshallAndAssertEquality(m2);
      byte[] bytes = marshaller.objectToByteBuffer(m4);
      Map<Integer, GlobalTransaction> m4Read = (Map<Integer, GlobalTransaction>) marshaller.objectFromByteBuffer(bytes);
      for (Map.Entry<Integer, GlobalTransaction> entry : m4.entrySet()) {
         assert m4Read.get(entry.getKey()).equals(entry.getValue()) : "Writen[" + entry.getValue() + "] and read[" + m4Read.get(entry.getKey()) + "] objects should be the same";
      }

      marshallAndAssertEquality(m5);
   }

   public void testSetMarshalling() throws Exception {
      Set s1 = new HashSet();
      Set s2 = new TreeSet();
      for (int i = 0; i < 10; i++) {
         Integer integ = new Integer(1000 * i);
         s1.add(integ);
         s2.add(integ);
      }
      marshallAndAssertEquality(s1);
      marshallAndAssertEquality(s2);
   }

   public void testMarshalledValueMarshalling() throws Exception {
      Person p = new Person();
      p.setName("Bob Dylan");
      MarshalledValue mv = new MarshalledValue(p, true);
      marshallAndAssertEquality(mv);
   }

   public void testSingletonListMarshalling() throws Exception {
      GlobalTransaction gtx = new GlobalTransaction(new JGroupsAddress(new IpAddress(12345)), false);
      List l = Collections.singletonList(gtx);
      marshallAndAssertEquality(l);
   }

   public void testTransactionLogMarshalling() throws Exception {
      GlobalTransaction gtx = new GlobalTransaction(new JGroupsAddress(new IpAddress(12345)), false);
      PutKeyValueCommand command = new PutKeyValueCommand("k", "v", false, null, 0, 0);
      TransactionLog.LogEntry entry = new TransactionLog.LogEntry(gtx, command);
      byte[] bytes = marshaller.objectToByteBuffer(entry);
      TransactionLog.LogEntry readObj = (TransactionLog.LogEntry) marshaller.objectFromByteBuffer(bytes);
      assert Arrays.equals(readObj.getModifications(), entry.getModifications()) :
            "Writen[" + entry.getModifications() + "] and read[" + readObj.getModifications() + "] objects should be the same";
      assert readObj.getTransaction().equals(entry.getTransaction()) :
            "Writen[" + entry.getModifications() + "] and read[" + readObj.getModifications() + "] objects should be the same";
   }

   public void testImmutableResponseMarshalling() throws Exception {
      marshallAndAssertEquality(RequestIgnoredResponse.INSTANCE);
      marshallAndAssertEquality(UnsuccessfulResponse.INSTANCE);
   }

   public void testExtendedResponseMarshalling() throws Exception {
      SuccessfulResponse sr = new SuccessfulResponse("Blah");
      ExtendedResponse extended = new ExtendedResponse(sr, false);
      byte[] bytes = marshaller.objectToByteBuffer(extended);
      ExtendedResponse readObj = (ExtendedResponse) marshaller.objectFromByteBuffer(bytes);
      assert extended.getResponse().equals(readObj.getResponse()) :
            "Writen[" + extended.getResponse() + "] and read[" + readObj.getResponse() + "] objects should be the same";
      assert extended.isReplayIgnoredRequests() == readObj.isReplayIgnoredRequests() :
            "Writen[" + extended.isReplayIgnoredRequests() + "] and read[" + readObj.isReplayIgnoredRequests() + "] objects should be the same";
   }

   public void testReplicableCommandsMarshalling() throws Exception {
      StateTransferControlCommand c1 = new StateTransferControlCommand(true);
      byte[] bytes = marshaller.objectToByteBuffer(c1);
      StateTransferControlCommand rc1 = (StateTransferControlCommand) marshaller.objectFromByteBuffer(bytes);
      assert rc1.getCommandId() == c1.getCommandId() : "Writen[" + c1.getCommandId() + "] and read[" + rc1.getCommandId() + "] objects should be the same";
      assert Arrays.equals(rc1.getParameters(), c1.getParameters()) : "Writen[" + c1.getParameters() + "] and read[" + rc1.getParameters() + "] objects should be the same";

      ClusteredGetCommand c2 = new ClusteredGetCommand("key", "mycache");
      marshallAndAssertEquality(c2);

      // SizeCommand does not have an empty constructor, so doesn't look to be one that is marshallable.

      GetKeyValueCommand c4 = new GetKeyValueCommand("key", null);
      bytes = marshaller.objectToByteBuffer(c4);
      GetKeyValueCommand rc4 = (GetKeyValueCommand) marshaller.objectFromByteBuffer(bytes);
      assert rc4.getCommandId() == c4.getCommandId() : "Writen[" + c4.getCommandId() + "] and read[" + rc4.getCommandId() + "] objects should be the same";
      assert Arrays.equals(rc4.getParameters(), c4.getParameters()) : "Writen[" + c4.getParameters() + "] and read[" + rc4.getParameters() + "] objects should be the same";

      PutKeyValueCommand c5 = new PutKeyValueCommand("k", "v", false, null, 0, 0);
      marshallAndAssertEquality(c5);

      RemoveCommand c6 = new RemoveCommand("key", null, null);
      marshallAndAssertEquality(c6);

      // EvictCommand does not have an empty constructor, so doesn't look to be one that is marshallable.

      InvalidateCommand c7 = new InvalidateCommand(null, null, "key1", "key2");
      bytes = marshaller.objectToByteBuffer(c7);
      InvalidateCommand rc7 = (InvalidateCommand) marshaller.objectFromByteBuffer(bytes);
      assert rc7.getCommandId() == c7.getCommandId() : "Writen[" + c7.getCommandId() + "] and read[" + rc7.getCommandId() + "] objects should be the same";
      assert Arrays.equals(rc7.getParameters(), c7.getParameters()) : "Writen[" + c7.getParameters() + "] and read[" + rc7.getParameters() + "] objects should be the same";

      InvalidateCommand c71 = new InvalidateL1Command(null, null, "key1", "key2");
      bytes = marshaller.objectToByteBuffer(c71);
      InvalidateCommand rc71 = (InvalidateCommand) marshaller.objectFromByteBuffer(bytes);
      assert rc71.getCommandId() == c71.getCommandId() : "Writen[" + c71.getCommandId() + "] and read[" + rc71.getCommandId() + "] objects should be the same";
      assert Arrays.equals(rc71.getParameters(), c71.getParameters()) : "Writen[" + c71.getParameters() + "] and read[" + rc71.getParameters() + "] objects should be the same";

      ReplaceCommand c8 = new ReplaceCommand("key", "oldvalue", "newvalue", 0, 0);
      marshallAndAssertEquality(c8);

      ClearCommand c9 = new ClearCommand();
      bytes = marshaller.objectToByteBuffer(c9);
      ClearCommand rc9 = (ClearCommand) marshaller.objectFromByteBuffer(bytes);
      assert rc9.getCommandId() == c9.getCommandId() : "Writen[" + c9.getCommandId() + "] and read[" + rc9.getCommandId() + "] objects should be the same";
      assert Arrays.equals(rc9.getParameters(), c9.getParameters()) : "Writen[" + c9.getParameters() + "] and read[" + rc9.getParameters() + "] objects should be the same";

      Map m1 = new HashMap();
      for (int i = 0; i < 10; i++) {
         GlobalTransaction gtx = new GlobalTransaction(new JGroupsAddress(new IpAddress(1000 * i)), false);
         m1.put(1000 * i, gtx);
      }
      PutMapCommand c10 = new PutMapCommand(m1, null, 0, 0);
      marshallAndAssertEquality(c10);

      Address local = new JGroupsAddress(new IpAddress(12345));
      GlobalTransaction gtx = new GlobalTransaction(local, false);
      PrepareCommand c11 = new PrepareCommand(gtx, true, c5, c6, c8, c10);
      marshallAndAssertEquality(c11);

      CommitCommand c12 = new CommitCommand(gtx);
      marshallAndAssertEquality(c12);

      RollbackCommand c13 = new RollbackCommand(gtx);
      marshallAndAssertEquality(c13);

      MultipleRpcCommand c99 = new MultipleRpcCommand(Arrays.asList(new ReplicableCommand[]{c2, c5, c6, c8, c10, c12, c13}), "mycache");
      marshallAndAssertEquality(c99);
   }

   public void testInternalCacheEntryMarshalling() throws Exception {
      ImmortalCacheEntry entry1 = (ImmortalCacheEntry) InternalEntryFactory.create("key", "value", System.currentTimeMillis() - 1000, -1, System.currentTimeMillis(), -1);
      marshallAndAssertEquality(entry1);

      MortalCacheEntry entry2 = (MortalCacheEntry) InternalEntryFactory.create("key", "value", System.currentTimeMillis() - 1000, 200000, System.currentTimeMillis(), -1);
      marshallAndAssertEquality(entry2);

      TransientCacheEntry entry3 = (TransientCacheEntry) InternalEntryFactory.create("key", "value", System.currentTimeMillis() - 1000, -1, System.currentTimeMillis(), 4000000);
      marshallAndAssertEquality(entry3);

      TransientMortalCacheEntry entry4 = (TransientMortalCacheEntry) InternalEntryFactory.create("key", "value", System.currentTimeMillis() - 1000, 200000, System.currentTimeMillis(), 4000000);
      marshallAndAssertEquality(entry4);
   }
   
   public void testInternalCacheValueMarshalling() throws Exception {
      ImmortalCacheValue value1 = (ImmortalCacheValue) InternalEntryFactory.createValue("value", System.currentTimeMillis() - 1000, -1, System.currentTimeMillis(), -1);
      byte[] bytes = marshaller.objectToByteBuffer(value1);
      ImmortalCacheValue rvalue1 = (ImmortalCacheValue) marshaller.objectFromByteBuffer(bytes);
      assert rvalue1.getValue().equals(value1.getValue()) : "Writen[" + rvalue1.getValue() + "] and read[" + value1.getValue() + "] objects should be the same";

      MortalCacheValue value2 = (MortalCacheValue) InternalEntryFactory.createValue("value", System.currentTimeMillis() - 1000, 200000, System.currentTimeMillis(), -1);
      bytes = marshaller.objectToByteBuffer(value2);
      MortalCacheValue rvalue2 = (MortalCacheValue) marshaller.objectFromByteBuffer(bytes);
      assert rvalue2.getValue().equals(value2.getValue()) : "Writen[" + rvalue2.getValue() + "] and read[" + value2.getValue() + "] objects should be the same";
      
      TransientCacheValue value3 = (TransientCacheValue) InternalEntryFactory.createValue("value", System.currentTimeMillis() - 1000, -1, System.currentTimeMillis(), 4000000);
      bytes = marshaller.objectToByteBuffer(value3);
      TransientCacheValue rvalue3 = (TransientCacheValue) marshaller.objectFromByteBuffer(bytes);
      assert rvalue3.getValue().equals(value3.getValue()) : "Writen[" + rvalue3.getValue() + "] and read[" + value3.getValue() + "] objects should be the same";
      
      TransientMortalCacheValue value4 = (TransientMortalCacheValue) InternalEntryFactory.createValue("value", System.currentTimeMillis() - 1000, 200000, System.currentTimeMillis(), 4000000);
      bytes = marshaller.objectToByteBuffer(value4);
      TransientMortalCacheValue rvalue4 = (TransientMortalCacheValue) marshaller.objectFromByteBuffer(bytes);
      assert rvalue4.getValue().equals(value4.getValue()) : "Writen[" + rvalue4.getValue() + "] and read[" + value4.getValue() + "] objects should be the same";
   }
   
   public void testBucketMarshalling() throws Exception {
      ImmortalCacheEntry entry1 = (ImmortalCacheEntry) InternalEntryFactory.create("key", "value", System.currentTimeMillis() - 1000, -1, System.currentTimeMillis(), -1);
      MortalCacheEntry entry2 = (MortalCacheEntry) InternalEntryFactory.create("key", "value", System.currentTimeMillis() - 1000, 200000, System.currentTimeMillis(), -1);
      TransientCacheEntry entry3 = (TransientCacheEntry) InternalEntryFactory.create("key", "value", System.currentTimeMillis() - 1000, -1, System.currentTimeMillis(), 4000000);
      TransientMortalCacheEntry entry4 = (TransientMortalCacheEntry) InternalEntryFactory.create("key", "value", System.currentTimeMillis() - 1000, 200000, System.currentTimeMillis(), 4000000);
      Bucket b = new Bucket();
      b.setBucketName("mybucket");
      b.addEntry(entry1);
      b.addEntry(entry2);
      b.addEntry(entry3);
      b.addEntry(entry4);
      
      byte[] bytes = marshaller.objectToByteBuffer(b);
      Bucket rb = (Bucket) marshaller.objectFromByteBuffer(bytes);
      assert rb.getEntries().equals(b.getEntries()) : "Writen[" + b.getEntries() + "] and read[" + rb.getEntries() + "] objects should be the same";
   }
   
   public void testLongPutKeyValueCommand() throws Exception
   {
      PutKeyValueCommand c = new PutKeyValueCommand("SESSION_173", "@TSXMHVROYNOFCJVEUJQGBCENNQDEWSCYSOHECJOHEICBEIGJVTIBB@TVNCWLTQCGTEJ@NBJLTMVGXCHXTSVE@BCRYGWPRVLXOJXBRJDVNBVXPRTRLBMHPOUYQKDEPDSADUAWPFSIOCINPSSFGABDUXRMTMMJMRTGBGBOAMGVMTKUDUAJGCAHCYW@LAXMDSFYOSXJXLUAJGQKPTHUKDOXRWKEFIVRTH@VIMQBGYPKWMS@HPOESTPIJE@OTOTWUWIOBLYKQQPTNGWVLRRCWHNIMWDQNOO@JHHEVYVQEODMWKFKKKSWURVDLXPTFQYIHLIM@GSBFWMDQGDQIJONNEVHGQTLDBRBML@BEWGHOQHHEBRFUQSLB@@CILXEAVQQBTXSITMBXHMHORHLTJF@MKMHQGHTSENWILTAKCCPVSQIPBVRAFSSEXIOVCPDXHUBIBUPBSCGPRECXEPMQHRHDOHIHVBPNDKOVLPCLKAJMNOTSF@SRXYVUEMQRCXVIETXVHOVNGYERBNM@RIMGHC@FNTUXSJSKALGHAFHGTFEANQUMBPUYFDSGLUYRRFDJHCW@JBWOBGMGTITAICRC@TPVCRKRMFPUSRRAHI@XOYKVGPHEBQD@@APEKSBCTBKREWAQGKHTJ@IHJD@YFSRDQPA@HKKELIJGFDYFEXFCOTCQIHKCQBLVDFHMGOWIDOWMVBDSJQOFGOIAPURRHVBGEJWYBUGGVHE@PU@NMQFMYTNYJDWPIADNVNCNYCCCPGODLAO@YYLVITEMNNKIFSDXKORJYWMFGKNYFPUQIC@AIDR@IWXCVALQBDOXRWIBXLKYTWDNHHSCUROAU@HVNENDAOP@RPTRIGLLLUNDQIDXJDDNF@P@PA@FEIBQKSKFQITTHDYGQRJMWPRLQC@NJVNVSKGOGYXPYSQHKPALKLFWNAOSQFTLEPVOII@RPDNRCVRDUMMFIVSWGIASUBMTGQSDGB@TBBYECFBRBGILJFCJ@JIQIQRVJXWIPGNVXKYATSPJTIPGCMCNPOKNEHBNUIAEQFQTYVLGAR@RVWVA@RMPBX@LRLJUEBUWO@PKXNIP@FKIQSVWKNO@FOJWDSIOLXHXJFBQPPVKKP@YKXPOOMBTLXMEHPRLLSFSVGMPXXNBCYVVSPNGMFBJUDCVOVGXPKVNTOFKVJUJOSDHSCOQRXOKBVP@WCUUFGMJAUQ@GRAGXICFCFICBSNASUBPAFRIPUK@OXOCCNOGTTSFVQKBQNB@DWGVEFSGTAXAPLBJ@SYHUNXWXPMR@KPFAJCIXPDURELFYPMUSLTJSQNDHHKJTIWCGNEKJF@CUWYTWLPNHYPHXNOGLSICKEFDULIXXSIGFMCQGURSRTUJDKRXBUUXIDFECMPXQX@CVYLDABEMFKUGBTBNMNBPCKCHWRJKSOGJFXMFYLLPUVUHBCNULEFAXPVKVKQKYCEFRUYPBRBDBDOVYLIQMQBLTUK@PRDCYBOKJGVUADFJFAFFXKJTNAJTHISWOSMVAYLIOGIORQQWFAKNU@KHPM@BYKTFSLSRHBATQTKUWSFAQS@Y@QIKCUWQYTODBRCYYYIAFMDVRURKVYJXHNGVLSQQFCXKLNUPCTEJSWIJUBFELSBUHANELHSIWLVQSSAIJRUEDOHHX@CKEBPOJRLRHEPLENSCDGEWXRTVUCSPFSAJUXDJOIUWFGPKHBVRVDMUUCPUDKRKVAXPSOBOPKPRRLFCKTLH@VGWKERASJYU@JAVWNBJGQOVF@QPSGJVEPAV@NAD@@FQRYPQIOAURILWXCKINPMBNUHPUID@YDQBHWAVDPPWRFKKGWJQTI@@OPSQ@ROUGHFNHCJBDFCHRLRTEMTUBWVCNOPYXKSSQDCXTOLOIIOCXBTPAUYDICFIXPJRB@CHFNXUCXANXYKXAISDSSLJGQOLBYXWHG@@KPARPCKOXAYVPDGRW@LDCRQBNMJREHWDYMXHEXAJQKHBIRAVHJQIVGOIXNINYQMJBXKM@DXESMBHLKHVSFDLVPOSOVMLHPSHQYY@DNMCGGGAJMHPVDLBGJP@EVDGLYBMD@NWHEYTBPIBPUPYOPOJVV@IVJXJMHIWWSIRKUWSR@U@@TDVMG@GRXVLCNEIISEVIVPOMJHKOWMRMITYDUQASWJIKVNYUFQVDT@BHTOMFXVFRKAARLNOGX@ADWCKHOVEMIGBWXINCUXEMVHSJJQDU@INTHDJQPSAQNAYONDBBFYGBTNGUSJHRKLCPHQMNLDHUQJPLLCDVTYLXTHJCBUXCRDY@YI@IQDCLJBBJC@NXGANXFIWPPNFVTDJWQ@@BIYJONOFP@RHTQEYPVHPPUS@UUENSNNF@WVGTSAVKDSQNMHP@VJORGTVWXVBPWKQNRWLSQFSBMXQKWRYMXPAYREXYGONKEWJMBCSLB@KSHXMIWMSBDGQWPDMUGVNMEWKMJKQECIRRVXBPBLGAFTUFHYSHLF@TGYETMDXRFAXVEUBSTGLSMWJMXJWMDPPDAFGNBMTQEMBDLRASMUMU@QTCDCPEGODHESDQVEIQYBJJPFXDLWPUNFAREYCY@YDDSTMKWCANNPXF@@WLMEXRPUNTWNOX@YKFNNTGMXIBBDA@TYLPJFNFHPQKMSNCLBME@FBPOIYNSDFBLHITKIFEFNXXOJAAFMRTGPALOANXF@YPY@RYTVOW@AKNM@C@LJKGBJMUYGGTXRHQCPOLNOGPPS@YSKAJSTQHLRBXUACXJYBLJSEHDNMLLUBSOIHQUI@VUNF@XAVRXUCYNCBDDGUDNVRYP@TPFPKGVNPTEDOTTUUFKCHQ@WWASQXLCBHNRBVSD@NVYT@GJQYSQGYPJO@WSEYDVKCBWANAFUWLDXOQYCYP@BSJFCBTXGKUNWLWUCYL@TNOWGDFHQTWQVYLQBBRQVMGNDBVXEFXTMMVYSHNVTTQAJCHKULOAJUSGJRPHQFCROWE@OMFUVRKGCWED@IAQGRLADOJGQKLCL@FCKTSITGMJRCCMPLOS@ONPQWFUROXYAUJQXIYVDCYBPYHPYCXNCRKRKLATLWWXLBLNOPUJFUJEDOIRKS@MMYPXIJNXPFOQJCHSCBEBGDUQYXQAWEEJDOSINXYLDXUJCQECU@WQSACTDFLGELHPGDFVDXFSSFOSYDLHQFVJESNAVAHKTUPBTPLSFSHYKLEXJXGWESVQQUTUPU@QXRTIDQ@IXBBOYINNHPEMTPRVRNJPQJFACFXUBKXOFHQSPOTLCQ@PLWGEFNKYCYFMKWPFUP@GLHKNMASGIENCACUISTG@YNQCNSOSBKOIXORKSHEOXHSMJJRUICJTCK@PWFRBPLXU@MUEMPFGDLUJEKD@ROUFBLKATXUCHEAQHEYDLCFDIRJSAXTV@CYMPQNMLTMFAHPRBLNSCVFBJMKQLAHWYIOLRMTOY@@RNKTUXHFYUMHGKCCGNEOIOQCISJEHCEVTTWM@TLFRIFDREHFBTTDEJRUNTWAEETGSVDOR@@UQNKFERMBVFJBOAYHPOKMSMRIERDA@JXYSJ@ORER@MBAVWCVGFNA@FRRPQSIIOIUGAJKVQXGINUUKPJPLQRMHPUBETEEIMIBPM@PETR@XD@DOHGRIBVXKLXQWHUFMTWEDYWFWRLPGDS@TANUXGIDTRVXKVCVEXYRKXQCTI@WNSFRAHJJGG@NIPPAAOJXQRTCLBYKDA@FFGHNUIGBFKOQMEDUEFELFLNKPCHA@OXJJRYNPDFSXIFSJYTDMSSBHDPUSQQDAVD@JAAWJDSVTERAJBFEPVRWKMYAPISPWLDPSRE@UMRQLXERTWRDLQVMVCOM@NYPXFLWMWKALMQVNJ@HCTMMIOLRWBJHCYFLMM@IWXPSHRRUNICSSWHOQHUVJE@HKJAADLBTPVLDAKCHRSURJCAXYTMYKHQMWDAWWASUW@HWGBVPTRHJGDWOGHPCNWSXTNKWONQGEKDDWGCKWVSAD@YLCCENMCHALHVDYQW@NQGNCY@M@GGV@RIR@OUS@PQIJMCFEIMGPYBXYR@NSIAUEXT@MOCNWRMLYHUUAFJCCLLRNFGKLPPIIH@BYRME@UJAKIFHOV@ILP@BGXRNJBIBARSOIMTDSHMGPIGRJBGHYRYXPFUHVOOMCQFNLM@CNCBTGO@UKXBOICNVCRGHADYQVAMNSFRONJ@WITET@BSHMQLWYMVGMQJVSJOXOUJDSXYVVBQJSVGREQLIQKWC@BMDNONHXFYPQENSJINQYKHVCTUTG@QQYJKJURDCKJTUQAM@DWNXWRNILYVAAJ@IADBIXKEIHVXLXUVMGQPAQTWJCDMVDVYUDTXQTCYXDPHKBAGMTAMKEM@QNOQJBREXNWFCXNXRPGOGEIR@KQJIGXAWXLTNCX@ID@XNRNYGRF@QPNWEX@XH@XKSXLQTLQPFSHAHXJLHUTNQWFFAJYHBWIFVJELDPSPLRRDPPNXSBYBEREEELIWNVYXOXYJQAIGHALUAWNUSSNMBHBFLRMMTKEKNSINECUGWTDNMROXI@BJJXKSPIIIXOAJBFVSITQDXTODBGKEPJMWK@JOL@SWTCGSHCOPHECTPJFUXIHUOSVMUTNNSLLJDEOMAGIXEAAVILRMOJXVHHPNPUYYODMXYAYGHI@BUB@NLP@KNPCYFRWAFES@WISBACDSPELEVTJEBNRVENSXXEVDVC@RIDIDSBPQIQNNSRPS@HCJ@XPIOFDXHUBCNFQKHMUYLXW@LMFMALHLESSXCOULRWDTJIVKKTLGFE@HKGVKUGMVHWACQOTSVNWBNUUGTMSQEJ@DXJQQYPOWVRQNQKXSLOEAA@@FRDCGCCQWQ@IY@EATGQGQIETPIJHOIQRYWLTGUENQYDNQSBI@IAUDEWDKICHNUGNAIXNICMBK@CJGSASMTFKWOBSI@KULNENWXV@VNFOANM@OJHFVV@IYRMDB@LHSGXIJMMFCGJKTKDXSMY@FHDNY@VSDUORGWVFMVKJXOCCDLSLMHCSXFBTW@RQTFNRDJUIKRD@PWPY", false, null, 0, 0);
      marshallAndAssertEquality(c);
   }

   protected void marshallAndAssertEquality(Object writeObj) throws Exception {
      byte[] bytes = marshaller.objectToByteBuffer(writeObj);
      Object readObj = marshaller.objectFromByteBuffer(bytes);
      assert readObj.equals(writeObj) : "Writen[" + writeObj + "] and read[" + readObj + "] objects should be the same";
   }

}
