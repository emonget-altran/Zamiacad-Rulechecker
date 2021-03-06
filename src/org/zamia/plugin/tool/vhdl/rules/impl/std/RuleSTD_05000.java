package org.zamia.plugin.tool.vhdl.rules.impl.std;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.zamia.ZamiaProject;
import org.zamia.plugin.tool.vhdl.ClockSignal;
import org.zamia.plugin.tool.vhdl.EntityException;
import org.zamia.plugin.tool.vhdl.HdlArchitecture;
import org.zamia.plugin.tool.vhdl.HdlEntity;
import org.zamia.plugin.tool.vhdl.HdlFile;
import org.zamia.plugin.tool.vhdl.Process;
import org.zamia.plugin.tool.vhdl.ReportFile;
import org.zamia.plugin.tool.vhdl.ResetSignal;
import org.zamia.plugin.tool.vhdl.Sensitivity;
import org.zamia.plugin.tool.vhdl.Signal;
import org.zamia.plugin.tool.vhdl.manager.RegisterAffectationManager;
import org.zamia.plugin.tool.vhdl.rules.RuleE;
import org.zamia.plugin.tool.vhdl.rules.RuleResult;
import org.zamia.plugin.tool.vhdl.rules.impl.Rule;
import org.zamia.plugin.tool.vhdl.rules.impl.RuleManager;
import org.zamia.plugin.tool.vhdl.rules.impl.SensitivityRuleViolation;
import org.zamia.util.Pair;
import org.zamia.vhdl.ast.Architecture;
import org.zamia.vhdl.ast.Entity;

/*
 * Sensitivity list for synchronous processes.
 * A synchronous process needs only the clock and reset signals in the sensitivity list.
 * No Parameters.
 */
public class RuleSTD_05000 extends Rule {

	private HdlFile _hdlFile;
	private Entity _entity;
	private Architecture _architecture;
	private List<SensitivityRuleViolation> _violations;
	
	public RuleSTD_05000() {
		super(RuleE.STD_05000);
	}

	@Override
	public Pair<Integer, RuleResult> Launch(ZamiaProject zPrj, String ruleId, ParameterSource parameterSource) {
		
		initializeRule(parameterSource, ruleId);
		
		//// Make register list
		
		Map<String, HdlFile> hdlFiles;
		try {
			hdlFiles = RegisterAffectationManager.getRegisterAffectation();
		} catch (EntityException e) {
			LogNeedBuild();
			return new Pair<Integer, RuleResult> (RuleManager.NO_BUILD, null);
		}

		//// Check rule
		
		_violations = new ArrayList<SensitivityRuleViolation>();
		
		for (Entry<String, HdlFile> entry : hdlFiles.entrySet()) {
			HdlFile hdlFile = entry.getValue();
			if (hdlFile.getListHdlEntity() == null) 
				continue;
			
			_hdlFile = hdlFile;
			for (HdlEntity hdlEntityItem : _hdlFile.getListHdlEntity()) {
				_entity = hdlEntityItem.getEntity();
				if (hdlEntityItem.getListHdlArchitecture() == null)
					continue;
				
				for (HdlArchitecture hdlArchitectureItem : hdlEntityItem.getListHdlArchitecture()) {
					_architecture = hdlArchitectureItem.getArchitecture();
					if (hdlArchitectureItem.getListProcess() == null) 
						continue;
					
					for (Process process : hdlArchitectureItem.getListProcess()) {
						if (!process.isSynchronous()) 
							continue;
						
						checkSensitivityList(process);
						checkSensitivityUsed(process);
					}
				}
			}

		}

		//// Write report
		
		Pair<Integer, RuleResult> result = null;
		
		ReportFile reportFile = new ReportFile(this);
		if (reportFile.initialize()) {
			for (SensitivityRuleViolation violation : _violations) {
				violation.generate(reportFile);
			}

			result = reportFile.save();
		}
		
		return result;
	}

	private void checkSensitivityUsed(Process process) {
		for (Sensitivity sensitivity : process.getListSensitivity()) {
			boolean find = false;
			for (ClockSignal clockSignalItem : process.getListClockSignal()) {
				if (checkSensitivityUsedInSignal(sensitivity, clockSignalItem)) {
					find = true;
				}

				if (!find && clockSignalItem.hasSynchronousReset()) {
					for (ResetSignal resetSignalItem : clockSignalItem.getListResetSignal()) {
						if (checkSensitivityUsedInSignal(sensitivity, resetSignalItem)) {
							find = true;
						}
					}
				}
			}
			if (!find && (sensitivity.isVector() || sensitivity.isPartOfVector())) {
				for (ClockSignal clockSignalItem : process.getListClockSignal()) {
					if (sensitivity.getVectorName().equalsIgnoreCase(clockSignalItem.getVectorName())) {
						find = true;
					}

					if (!find && clockSignalItem.hasSynchronousReset()) {
						for (ResetSignal resetSignalItem : clockSignalItem.getListResetSignal()) {
							if (sensitivity.getVectorName().equalsIgnoreCase(resetSignalItem.getVectorName())) {
								find = true;
							}
						}
					}
				}
				
				if (find) {
					for (int i = sensitivity.getIndexMin() ; i <= sensitivity.getIndexMax(); i++) {
						boolean findIndex = false;
						String vectorName = sensitivity.toString()+"("+i+")";
						for (ClockSignal clockSignalItem : process.getListClockSignal()) {
							if (vectorName.toString().equalsIgnoreCase(clockSignalItem.toString())) {
								findIndex = true;
							}
							if (!find && clockSignalItem.hasSynchronousReset()) {
								for (ResetSignal resetSignalItem : clockSignalItem.getListResetSignal()) {
									if (vectorName.toString().equalsIgnoreCase(resetSignalItem.toString())) {
										findIndex = true;
									}
								}
							}
						}
						
						if (!findIndex) {
							String fileName = _hdlFile.getLocalPath();
							int line = sensitivity.getLocation().fLine; 
							String sensitivityName = vectorName;
							_violations.add(
									new SensitivityRuleViolation(fileName, line, _entity, _architecture, process, sensitivityName, true, false));
						}
					}
					
				}
				System.out.println("checkSensitivityUsed  "+ " type "+sensitivity.getType() );
			}

			if (!find) {
				String fileName = _hdlFile.getLocalPath();
				int line = sensitivity.getLocation().fLine; 
				String sensitivityName = sensitivity.toString();
				_violations.add(
						new SensitivityRuleViolation(fileName, line, _entity, _architecture, process, sensitivityName, true, false));
			}
		}

	}


	private boolean checkSensitivityUsedInSignal(Sensitivity sensitivity, Signal signal) {
		boolean find = false;
		if (sensitivity.toString().equalsIgnoreCase(signal.toString())) {
			find = true;
		}
		return find;
	}


	private void checkSensitivityList(Process processItem) {
		for (ClockSignal clockSignalItem : processItem.getListClockSignal()) {
			checkSensitivityRegister(clockSignalItem, processItem.getListSensitivity(),
					processItem, clockSignalItem);

			if (clockSignalItem.hasSynchronousReset()) {
				for (ResetSignal resetSignalItem : clockSignalItem.getListResetSignal()) {
					checkSensitivityRegister(resetSignalItem, processItem.getListSensitivity(),
							processItem, clockSignalItem, resetSignalItem);
				}
			}
		}
	}

	private void checkSensitivityRegister(Signal signal, ArrayList<Sensitivity> listSensitivity,
			Process process, ClockSignal clockSignal) {
		checkSensitivityRegister(signal, listSensitivity, process, clockSignal, null);
	}

	private void checkSensitivityRegister(Signal signal, ArrayList<Sensitivity> listSensitivity,
			Process process, ClockSignal clockSignal, ResetSignal resetSignal) {
		boolean find = false;
		for (Sensitivity sensitivity : listSensitivity) {
			if (signal.toString().equalsIgnoreCase(sensitivity.toString())) {
				find = true;
			}
		}
		// case not defined : partial used  (vector)
		if (!find && (signal.isVector() || signal.isPartOfVector())) {
			System.out.println("test part signal "+ signal.toString()+ "  vector name "+signal.getVectorName());
			for (Sensitivity sensitivity : listSensitivity) {
				if (signal.getVectorName().equalsIgnoreCase(sensitivity.toString())) {
					find = true;
				}
			}
		}
		// case not defined : partial defined  (vector)
		if (!find && (signal.isVector() || signal.isPartOfVector())) {

			for (Sensitivity sensitivity : listSensitivity) {
				System.out.println("sensitivity "+sensitivity.toString()+ "  vectorname "+sensitivity.getVectorName());
				if (signal.getVectorName().equalsIgnoreCase(sensitivity.getVectorName())) {
					find = true;
				}
			}

			if (find) {

				System.out.println("test part signal "+ signal.toString()+ "  vector name "+signal.getVectorName());
				int indexMin = 0;
				int indexMax = 0;
				if (signal.isAscending()) {
					indexMin = signal.getLeft();
					indexMax = signal.getRight();
				} else {
					indexMax = signal.getLeft();
					indexMin = signal.getRight();
				}
				for (int i = indexMin ; i <= indexMax; i++) {
					boolean findIndex = false;
					String vectorName = signal.toString()+"("+i+")";
					for (Sensitivity sensitivity : listSensitivity) {
						System.out.println("sensitivity "+sensitivity.toString()+ "  vectorname "+sensitivity.getVectorName());
						if (vectorName.toString().equalsIgnoreCase(sensitivity.toString())) {
							findIndex = true;
							find = true;
						}
					}
					if (!findIndex) {
						String fileName = _hdlFile.getLocalPath();
						int line = signal.getLocation().fLine; 
						String sensitivityName = vectorName;
						_violations.add(
								new SensitivityRuleViolation(fileName, line, _entity, _architecture, process, sensitivityName, true, true));
					}
				}
			}
		}

		if (!find) {
			String fileName = _hdlFile.getLocalPath();
			int line = signal.getLocation().fLine; 
			String sensitivityName = signal.toString();
			_violations.add(
					new SensitivityRuleViolation(fileName, line, _entity, _architecture, process, sensitivityName, true, true));
		}
	}
}
